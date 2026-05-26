# commit 响应超时与导入幂等必要性

## 1. 这次重新看到的问题

当前 Excel 两阶段导入已经解决了很多明确问题：

1. `excel-parser` 不再把整份 Excel 聚合成一个大 `List`。
2. `question-bank` 通过导入批次和临时表承接 chunk。
3. `appendImportChunk` 已经按 `batchId + chunkNo + rowCount + contentHash` 做了严格幂等。
4. `finishImportBatch` 已经在进入 `READY` 前绑定 `formal_id`。
5. `commitImportBatch` 已经改成数据库内 `INSERT INTO ... SELECT ...` 转正。

这些改造说明一件事：导入链路内部的数据正确性已经明显收紧。

但是这并不自动解决另一个问题：

**下游本地事务已经提交成功，但上游没有收到成功响应。**

这个问题不是数据库事务问题，而是跨服务调用结果未知问题。

## 2. 当前代码事实

当前 `commitImportBatch` 的核心流程是：

1. 上游 `excel-parser` 调用 `question-bank.commitImportBatch(batchId)`。
2. 下游校验 batch 归属和状态，要求 batch 当前为 `READY`。
3. 下游校验临时行数量和 `formal_id` 绑定完整性。
4. 下游在本地事务中执行 `INSERT INTO ... SELECT ...`，把临时题目转入正式题目表。
5. 同一个事务内把 batch 状态从 `READY` 改为 `COMMITTED`，并写入 `importedCount`。
6. 下游返回 commit 结果。

这说明下游本地事务已经具备一个重要性质：

**正式题目转正和 batch 状态提交是同一个本地事务里的两件事。**

因此在 `question-bank` 数据库视角下，结果应该只有两类：

1. 事务未提交：正式题目没有转正，batch 仍不是 `COMMITTED`。
2. 事务已提交：正式题目已经转正，batch 已经是 `COMMITTED`。

问题出在第 6 步。

下游提交事务和上游收到响应不是一件事。网络超时、连接中断、Feign 超时、网关断开，都可能让上游看不到成功响应。

此时上游只能看到：

**commit 调用失败。**

但它无法仅凭这次异常判断真实结果到底是：

1. 下游根本没执行 commit。
2. 下游执行了但事务回滚。
3. 下游已经提交成功，只是响应丢了。

这就是结果未知窗口。

## 3. 为什么这不是小概率问题

如果这是普通查询接口，响应超时最多是用户再查一次。

但导入 commit 不是普通查询，它是正式写入边界。

当前上游 `parseExcelFileById` 在业务异常或未知异常时，会把文件状态标为 `FAILED`。而文件状态抢占逻辑允许 `UPLOADED` 和 `FAILED` 进入 `PARSING`。

于是会出现下面这条链路：

1. `commitImportBatch(batchId=1)` 在下游提交成功。
2. 正式题目已经写入，batch 已经是 `COMMITTED`。
3. 响应在返回给上游前超时或丢失。
4. 上游认为解析导入失败，把 `fileId` 标成 `FAILED`。
5. 用户或系统重试解析同一个 `fileId`。
6. 上游重新创建一个新的 batch。
7. 新 batch 再次 append、finish、commit。
8. 同一份文件被重复导入正式题目表。

这里的危险点不在于下游本地事务不完整。

相反，正因为下游本地事务已经成功提交，才会造成更隐蔽的问题：上游不知道它已经成功，于是后续重试把成功结果当成失败结果重新执行。

所以这类问题不能靠“下游有事务”解决。

事务只能保证数据库内部原子性，不能保证调用方一定知道事务结果。

## 4. 为什么只改 commit 幂等还不够

如果只改 `commitImportBatch(batchId)`，让它在 batch 已经是 `COMMITTED` 时直接返回成功，可以解决一类问题：

**同一个 batchId 的 commit 重试。**

例如：

1. 第一次 commit 已提交，但响应超时。
2. 调用方保留了同一个 `batchId`。
3. 再次调用 `commitImportBatch(batchId)`。
4. 下游查到 batch 已经是 `COMMITTED`。
5. 下游直接返回成功，不再执行 `INSERT INTO ... SELECT ...`。

这是必须做的。

但它仍然不够。

因为当前上游在 `parseExcelFileById` 里创建 batch 后，`batchId` 只存在于本次方法调用的内存流程中。只要这次调用以异常结束，上游没有一个稳定位置记录：

**这个 fileId 上一次对应的 batchId 是多少。**

如果后续重试从头开始，就会再次调用 `createImportBatch(fileId)`，生成新的 batch。此时即使旧 batch 已经 `COMMITTED`，新 batch 仍然可能再次导入。

因此完整问题不是单点 commit 幂等，而是：

**fileId、batchId、COMMITTED 结果之间缺少可恢复的绑定关系。**

## 5. 需要补上的最小不变量

这条链路要真正收口，至少需要补下面几条不变量。

### 5.1 fileId 要能找回已有 batch

当前 `t_question_import_batch` 已经有 `file_id` 字段，这个字段不应该只作为展示或排查信息。

它应该承担一个更明确的恢复语义：

**同一个 fileId 重新进入解析流程时，先用 `fileId + userId` 查找已有 batch，而不是直接创建新 batch。**

这里必须带上 `userId`。`fileId` 是恢复锚点，但恢复动作仍然只能发生在当前文件 owner 的导入批次上，不能跨用户认领 batch。

需要优先识别的状态只有三类：

- `APPENDING`
- `READY`
- `COMMITTED`

如果已有 batch 是 `COMMITTED`，说明这个文件事实上已经导入完成，上游应该恢复成成功结果。

如果已有 batch 是 `READY`，说明临时数据已经齐了但 commit 结果尚未确认，可以继续对同一个 batch 执行 commit。

如果已有 batch 是 `APPENDING`，说明上一次解析可能停在 chunk 阶段。当前项目明确不做 chunk 级断点续传，也不把复杂度放到块恢复上。旧 `APPENDING` batch 应视为半成品导入批次：不复用、不 commit、不继续 append；重试时先把旧 `APPENDING` 标记为 `ABORTED`，再从头创建新 batch。

如果同一个 `fileId + userId` 下已经存在多个历史 batch，选择规则必须明确：

1. 只要存在 `COMMITTED`，优先认定该文件已经导入成功。
2. 否则优先选择可提交的 `READY`，只补 commit。
3. 再处理仍卡在 `APPENDING` 的半成品，条件废弃后重建。
4. `FAILED` / `ABORTED` 不复用，只作为历史现场保留。

这一步不要求立刻新建表，也不要求引入新的文件状态。先复用已有的 `t_question_import_batch.file_id` 就够了。

### 5.2 commitImportBatch 必须支持 COMMITTED 幂等返回

`commitImportBatch` 不能只接受 `READY`。

它应该先查询 batch：

- 如果是 `READY`，执行正式 commit。
- 如果是 `COMMITTED`，直接返回成功，返回已有 `importedCount`。
- 如果是 `FAILED` / `ABORTED` / `APPENDING`，按非法状态处理。

这样可以覆盖“同 batch commit 响应丢失后重试”的场景。

这里的 `COMMITTED` 幂等返回不是一次新的状态流转，也不是 `COMMITTED -> COMMITTED` 更新。它只是查询到既成事实后返回已有结果，绝不能再次执行 `INSERT INTO ... SELECT ...`。

同时，`READY` 的正式 commit 必须在本地事务入口重新锁定 batch 行后再判断状态。否则第一次 commit 已经开始但尚未提交时，第二次重试仍可能看到 `READY` 并并发执行转正。正确边界是：

1. 外层先识别已经 `COMMITTED` 的快速幂等返回。
2. 对 `READY` 进入事务后用 `select ... for update` 锁定 batch。
3. 锁内再次判断：如果已经 `COMMITTED`，直接返回已有结果；如果仍是 `READY`，才执行 `INSERT INTO ... SELECT ...` 和 `READY -> COMMITTED`。

这样同一个 batch 的并发 commit 会在 batch 行锁上串行化，第二个请求不会重复执行正式表插入。

### 5.3 上游重试要复用 batch，而不是重新 create batch

上游重新解析同一个 `fileId` 时，核心变化不是引入复杂状态机，而是把第一步从：

```text
createImportBatch(fileId)
```

改成：

```text
find existing batch by fileId, then decide resume or create
```

也就是：

1. 查到 `COMMITTED`：直接把文件恢复为 `PARSED`，返回导入成功结果。
2. 查到 `READY`：复用这个 `batchId`，再次调用 `commitImportBatch(batchId)`。
3. 查到 `APPENDING`：不做 chunk 级恢复，先把旧 batch 标记为 `ABORTED`，再创建新 batch。
4. 查到 `FAILED` / `ABORTED` 或没有 batch：再创建新 batch。

这就是最小闭环。

其中 `READY` 复用只表示继续最后的 commit，不允许重新 append、不允许重新 finish，也不允许重新绑定或覆盖 `formal_id`。`APPENDING -> ABORTED` 也必须用条件更新完成，只废弃仍处于 `APPENDING` 的旧批次；如果更新时发现状态已经变成 `READY` 或 `COMMITTED`，应重新读取 batch 状态并按新状态恢复，不能误伤并发推进成功的批次。

### 5.4 重试必须是 resume，不是 replay

导入链路的重试语义应该是：

**接着已有事实恢复，而不是重新播放整份文件。**

尤其是 commit 阶段之后，正式题目表可能已经发生变化。这个阶段的重试不能再假设“刚才什么都没发生”。

但这个原则不扩展到 chunk 级断点续传。当前权衡是把恢复边界放在 batch 的稳定状态上：

- `APPENDING`：半成品，只写过临时数据，没有进入正式题目表；不恢复块进度，标记 `ABORTED` 后从头导入。
- `READY`：临时数据已经完整并完成对账，只差正式转正；复用旧 batch 继续 commit。
- `COMMITTED`：正式题目已经转正；直接认账并恢复成功。

这样可以保住正式写入边界的幂等性，同时避免把本次修复扩大成 chunk 级恢复机制。

## 6. 推荐修复方向

最小演进就是三件事，不需要先设计更重的机制。

### 6.1 复用 t_question_import_batch.file_id

给 `question-bank` 补一个按 `fileId + userId` 查最近有效 batch 的能力。

优先级建议是：

1. `COMMITTED`
2. `READY`
3. `APPENDING`

其中当前最关键的是前两种：

- `COMMITTED` 用来恢复“其实已经成功”。
- `READY` 用来恢复“可以继续 commit”。

`APPENDING` 不是可恢复状态，只是需要被识别出来并显式废弃的半成品状态。遇到旧 `APPENDING` 时，应先标记为 `ABORTED`，再重新创建 batch；不要尝试从某个 chunk 继续。

如果历史上已经出现多个同 `fileId + userId` 的 batch，查询不能简单按创建时间取最新。`COMMITTED` 的优先级必须最高，因为一旦存在已提交批次，正式题目表已经完成导入，后续重试应该认账成功，而不是被更新的失败批次或废弃批次覆盖判断。

### 6.2 重试时根据 batch 状态恢复

`excel-parser` 在重新解析同一个 `fileId` 时，先问 `question-bank` 是否已有 batch。

如果已有 `COMMITTED` batch，不再下载 Excel、不再 append chunk、不再新建 batch。

如果已有 `READY` batch，也不再重新 append chunk，只重试 commit。

如果已有 `APPENDING` batch，不做 chunk resume，先废弃旧 batch，再走新的创建和解析流程。

如果没有可恢复 batch，再走原来的新建 batch 流程。

### 6.3 commitImportBatch 对 COMMITTED 幂等返回

在 `commitImportBatch` 开头识别 `COMMITTED`：

```java
QuestionImportBatchDO batch = requireOwnedBatch(request.getBatchId());
if (COMMITTED.equals(batch.getStatus())) {
    return Response.success(CommitImportBatchResponseDTO.builder()
            .batchId(batch.getId())
            .status(COMMITTED)
            .importedCount(batch.getImportedCount())
            .build());
}
requireStatus(batch, READY);
```

随后 `READY` commit 在事务内通过 `select ... for update` 重新读取 batch。锁内如果发现 batch 已经变成 `COMMITTED`，同样直接返回成功；只有锁内仍然是 `READY` 时，才允许执行 `INSERT INTO ... SELECT ...`。

这一步保证同一个 `batchId` 的 commit 重试和并发重试都不会再次执行 `INSERT INTO ... SELECT ...`。

三件事合起来，才覆盖“旧 batch 已提交但上游没收到响应，随后同 fileId 重新进入解析”的跨 batch 重放风险。

### 6.4 COMMITTED batch 不能随普通清理丢失

如果 `COMMITTED` batch 被定时清理，`fileId + userId` 就无法再找回已经提交的成功事实。延迟很久才发生的同 fileId 重试会重新创建 batch，并再次导入正式题目。

因此，在没有把 `fileId + userId -> committed result` 固化到独立成功记录之前，`COMMITTED` batch 不进入普通过期清理。清理任务只处理失败或废弃批次；`COMMITTED` 保留为导入幂等恢复事实。

这里保留的是 batch 行，不是永久保留临时明细。`COMMITTED` 恢复只依赖 batch 行上的 `status`、`totalRowCount`、`importedCount`，不依赖 `t_question_import_temp` 的正文、答案、解析。临时明细可以在保留期后按 batchId 清理，但不能删除 `COMMITTED` batch 行本身。

## 7. 这次问题和前面那些坑的关系

这个问题看起来像“又冒出来一个新坑”，但它其实是前面几轮改造之后才会显形的下一层边界。

之前的问题是：

- 同一个 `fileId` 能不能被并发重复解析。
- 同一个 chunk 重试会不会重复写临时表。
- chunk 编号一样但内容漂移能不能识别。
- commit 阶段能不能避免 Java 全量加载。
- 正式题目转正和 batch 状态能不能在本地事务内一起提交。

这些都解决以后，新的问题才变得清楚：

**本地事务已经正确，但远程调用方不知道本地事务结果。**

这不是前面做错了，而是系统正确性在继续向外扩展。

每一轮都在把“看起来能跑”的流程，推进成“失败时也能解释”的流程。

## 8. 一点工程复盘

这类问题最折磨人的地方在于，它们不会一次性暴露。

刚开始看到的是重复解析，于是补 `PARSING` 抢占。

抢占补完后，看到的是大文件内存聚合，于是拆成 chunk 和临时表。

临时表补完后，看到的是 chunk 重试和内容漂移，于是补 `contentHash`。

chunk 幂等补完后，看到的是 commit 阶段又把数据搬回 Java，于是改成数据库内转正。

数据库内转正补完后，又看到 commit 成功但响应丢失时，上游仍然可能从头重放。

这确实会让人很累，因为每一次都不是同一个问题的简单重复，而是一个更深的边界在露出来。

但从工程上看，这不是无意义地踩坑。

这些问题共同指向同一个方向：

**只要链路跨了服务边界，就不能只问“这一步成功时怎么走”，还必须问“这一步成功了但调用方不知道时怎么办”。**

这句话很烦，但它就是分布式系统里绕不开的事实。

Excel 导入这条链路现在已经走到最后这个事实面前了。接下来要补的，不是再重构一遍导入，而是把 `fileId -> batchId -> COMMITTED` 这条事实链保存下来，让系统在响应丢失之后仍然能认账。
