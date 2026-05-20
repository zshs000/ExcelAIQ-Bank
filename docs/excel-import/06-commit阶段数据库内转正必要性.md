# commit 阶段数据库内转正必要性

## 1. 背景

两阶段导入已经把 Excel 解析阶段从“整份文件进入 Java 内存”改成了“流式解析、分块写入临时表”。

这个改造解决了两个核心问题：

1. `excel-parser` 不再聚合整份 `List<QuestionDataDTO>`。
2. `excel-parser -> question-bank` 不再通过一次 Feign 调用传输整批题目。

但最终 `commitImportBatch` 仍然是整条链路里最重的一步。如果提交阶段继续采用“读取全部临时行 -> 组装全部正式题目对象 -> 批量 insert”的方式，内存压力只是从解析阶段转移到了提交阶段，并没有真正消失。

## 2. 为什么必须做

当前两阶段方案的核心语义是：

1. 临时表可以失败、可以残留、可以用于排查。
2. 正式题目表必须保持原子性。
3. 导入成功后，正式表要么完整出现这一批题目，要么一条都不出现。

因此，不能简单把 commit 拆成多个独立事务分批提交。那会把语义从“整批原子导入”改坏成“可能部分成功”。

同时，也不能接受 commit 阶段长期全量进入 Java 内存。因为这会带来几个明确风险：

1. 应用层需要同时持有全部临时行、全部正式题目对象，以及批量 ID 列表。
2. MyBatis 会构造很大的 `INSERT ... VALUES (...)` 动态 SQL，容易受到 SQL 长度、JDBC 参数数量、`max_allowed_packet` 等限制影响。
3. 数据库仍然要承担大事务本身的 undo、redo、binlog 和锁持有成本。
4. 数据量越大，commit 阶段越容易成为整条链路新的瓶颈。

所以这个问题不是普通性能优化，而是两阶段导入方案闭环的一部分：既要保留正式表原子性，又要避免应用层全量承载最终转正动作。

## 3. 推荐方向

推荐把最终转正收敛为数据库内部集合操作：

```sql
insert into t_question_bank (
  id,
  process_status,
  created_time,
  updated_time,
  created_by,
  last_review_mode,
  content,
  answer,
  analysis
)
select
  formal_id,
  'WAITING',
  now(),
  now(),
  #{userId},
  null,
  content,
  answer,
  analysis
from t_question_import_temp
where batch_id = #{batchId};
```

这个方向的核心不是取消大事务，而是去掉 commit 阶段额外叠加的 Java 全量内存和超大动态 SQL。

其中 `last_review_mode` 当前按 `null` 写入，保持和现有 `batchInsert` 中未设置该字段时的语义一致。

## 4. formal_id 的绑定时机

为支持 `INSERT INTO ... SELECT ...`，`t_question_import_temp` 需要增加 `formal_id` 字段，用来保存未来正式题目的主键。

`formal_id` 不建议在 `appendImportChunk` 阶段申请。

原因是当前系统明确不做 chunk 级断点续传，失败后走文件级重试。`appendImportChunk` 的职责应该继续保持单纯：

1. 校验批次状态。
2. 校验 chunk hash。
3. 处理同内容重复到达和内容漂移冲突。
4. 写临时表。
5. 累加批次计数。

更合适的时机是 `finishImportBatch` 对账通过之后、正式 commit 之前。此时 batch 的临时数据已经完整稳定，可以按页为临时行批量申请 ID 并回写 `formal_id`。

如果 ID 绑定失败，批次进入 `FAILED`，文件状态也进入 `FAILED`，由用户按文件级重新解析导入，不尝试恢复旧 batch。

状态边界需要定清楚：`formal_id` 全部绑定完成前，批次不应进入 `READY`。推荐把 ID 绑定作为 `finishImportBatch` 内部动作，只有“计数对账通过 + ID 全部绑定 + 绑定结果校验通过”之后，才执行 `APPENDING -> READY`。这样 `READY` 仍然表示“可以直接 commit”，不会出现“批次已经 READY 但只有部分临时行有 formal_id”的中间状态。

如果后续认为 ID 绑定耗时过长，也可以显式增加 `BINDING_IDS` 状态，但这会扩大状态机和清理逻辑的复杂度。当前更推荐先不扩散对外状态。

## 5. 初步落地思路

### 5.1 表结构

给临时表增加正式题目 ID 字段：

```sql
alter table t_question_import_temp
  add column formal_id bigint default null comment '正式题目ID';
```

可以补充索引，方便提交前校验和排查：

```sql
create index idx_question_import_temp_batch_formal_id
  on t_question_import_temp(batch_id, formal_id);
```

正式题目 ID 来自分布式 ID 服务，理论上全局唯一。为了避免实现错误导致同一批内重复绑定，提交前仍应校验：

```sql
select count(*) as total_count,
       count(formal_id) as bound_count,
       count(distinct formal_id) as distinct_bound_count
from t_question_import_temp
where batch_id = #{batchId};
```

要求 `bound_count = total_count` 且 `distinct_bound_count = total_count`。

### 5.2 ID 绑定

在 `finishImportBatch` 对账通过后，新增 ID 绑定步骤：

1. 按页读取当前 batch 的临时行。
2. 每页批量申请一组正式题目 ID。
3. 只给 `formal_id is null` 的临时行回写 ID。
4. 已经存在 `formal_id` 的行不覆盖，保证绑定结果一旦写入就保持稳定。
5. 绑定完成后校验 `formal_id is null` 的行数必须为 0，并校验 `count(distinct formal_id) = totalRowCount`。

这一步可以作为 `finish -> READY` 之前的内部动作，也可以引入更细的内部状态。但从当前模型看，保持对外状态不扩散更简单。

如果部分临时行已经绑定 `formal_id` 后发生失败，本 batch 直接进入 `FAILED`，不继续补绑，也不允许 commit。已经申请并写入的 ID 不回收，只作为失败现场随临时表保留，等待后续异步清理。这符合分布式 ID 不要求连续、失败后按文件级重新导入的既有取舍。

### 5.3 commit 转正

`commitImportBatch` 中不再读取全部临时行并组装 `QuestionDO` 列表，而是在本地事务中执行：

1. 校验批次状态为 `READY`。
2. 校验临时行数与 `totalRowCount` 一致。
3. 校验不存在 `formal_id is null` 的临时行。
4. 校验 `count(distinct formal_id) = totalRowCount`。
5. 执行 `INSERT INTO t_question_bank ... SELECT ... FROM t_question_import_temp`。
6. 将批次 `READY -> COMMITTED`。

第 5 步和第 6 步必须在同一个本地事务内完成，并继续使用条件状态流转：只有 `READY` 批次允许执行转正并流转为 `COMMITTED`。如果批次已经是 `COMMITTED`，说明正式表已经完成转正，不应再次执行 `INSERT INTO ... SELECT ...`。

`INSERT INTO ... SELECT ...` 可以按 `chunk_no, row_no` 排序，方便排查时观察插入顺序。但 ID 已经固化在每一行的 `formal_id` 上，commit 不再依赖 Java 列表下标对齐，这是这个方案相对当前实现的关键收益。

## 6. 仍需保留的边界

数据库内转正不是无限导入的理由。

即使采用 `INSERT INTO ... SELECT ...`，最终 commit 仍然是一个数据库大事务。因此以下边界仍然必须保留：

1. 最大导入行数限制。
2. commit 阶段压测。
3. 临时表保留与异步清理策略。
4. 文件级失败重试语义。

这项演进解决的是“commit 阶段不该再把整批数据搬回 Java”这个问题，不改变两阶段导入的核心业务语义。
