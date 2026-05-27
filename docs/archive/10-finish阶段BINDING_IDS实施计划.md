# finish 阶段 BINDING_IDS 实施计划

> **用途**：本文档用于归档和后续实现 `QuestionImportBatchAppService.finishImportBatch` 的事务边界修复。  
> **目标**：引入内部状态 `BINDING_IDS`，让 `READY` 只在“数量对账通过、formal_id 全部绑定且唯一、最终校验与状态推进原子提交”后出现。  
> **约束**：不能改成一次性全量取号，必须保留每批 1000 的取号和绑定思路。

---

## 1. 背景问题

当前 `finishImportBatch` 的主要风险是：

1. finish 方法没有整体事务。
2. 分批绑定 `t_question_import_temp.formal_id` 时，每批更新可能已经提交。
3. 绑定完成后才执行 `APPENDING -> READY`。
4. `formal_id` 最终校验和 `READY` 状态推进不在同一个本地事务里。
5. finish 阶段没有锁住 batch 行，多个 finish 请求可能并发执行。

因此可能出现：

```text
t_question_import_batch.status = APPENDING
部分 t_question_import_temp.formal_id 已绑定
部分 t_question_import_temp.formal_id 仍为 null
```

这类状态不是 `READY`，但临时表已经被部分修改，属于 finish 阶段半成品。

---

## 2. 目标状态机

修复后导入批次状态机应调整为：

```text
APPENDING -> BINDING_IDS -> READY -> COMMITTED
       \                         \
        -> ABORTED / FAILED       -> FAILED
```

各状态语义：

| 状态 | 语义 | 允许动作 |
|---|---|---|
| `APPENDING` | 正在接收 chunk，尚未 finish 对账 | append、finish |
| `BINDING_IDS` | finish 数量对账已通过，正在补齐 temp 表 formal_id | finish 重试继续补绑定、后台恢复、超时失败 |
| `READY` | temp 行完整，formal_id 全部绑定且唯一，可以转正 | commit |
| `COMMITTED` | 已转正完成 | 幂等返回 |
| `FAILED` | 失败终态，保留现场 | 清理 |
| `ABORTED` | 中止终态 | 清理 |

关键约束：

1. `BINDING_IDS` 是内部状态，不允许 commit。
2. `BINDING_IDS` 不允许继续 append。
3. `BINDING_IDS` 允许 finish 重试继续处理。
4. `READY` 必须是硬语义：只有最终校验全部通过后才能进入。

---

## 3. 文件影响范围

### 3.1 生产代码

- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/enums/QuestionImportBatchStatusEnum.java`
  - 增加 `BINDING_IDS`。

- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/mapper/QuestionImportBatchDOMapper.java`
  - 增加 `markBindingIds`。
  - 可选：增加或复用 `markReady`，让其支持指定 expectedStatus。

- `eaqb-question-bank/eaqb-question-bank-biz/src/main/resources/mapper/QuestionImportBatchDOMapper.xml`
  - 增加 `APPENDING -> BINDING_IDS` 条件更新 SQL。
  - 修改 recover 查询，让 `BINDING_IDS` 可恢复。
  - 如有必要，调整 `markReady` 从 `BINDING_IDS -> READY`。

- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/imports/ImportBatchStateMachine.java`
  - 增加 `markBindingIdsOrThrow`。
  - 调整 `markReadyOrThrow` 的 expectedStatus，或者新增 `markReadyFromBindingIdsOrThrow`。

- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/imports/ImportWorkflowFacade.java`
  - 暴露 `markBindingIdsOrThrow` 和 `markReadyFromBindingIdsOrThrow`。

- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionImportBatchAppService.java`
  - 重写 finish 流程。
  - 支持 `APPENDING` 首次 finish 和 `BINDING_IDS` 重试恢复。
  - 保留每批 1000 绑定。
  - 最终校验与 `BINDING_IDS -> READY` 放入同一个本地事务，并锁 batch 行。

- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionImportBatchCleanupScheduler.java`
  - 考虑长期停留 `BINDING_IDS` 的 batch。
  - 最小方案：超时标记 `FAILED`。
  - 增强方案：后台尝试恢复绑定，失败或超过阈值再 `FAILED`。

### 3.2 测试代码

- `eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionImportBatchAppServiceTest.java`
  - 覆盖 finish 主流程、重试、并发幂等边界、最终事务校验。

- `eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionImportBatchCleanupSchedulerTest.java`
  - 覆盖 `BINDING_IDS` 超时处理。

---

## 4. 推荐实现顺序

### Task 1：补状态枚举和 Mapper 能力

目标：让代码层面能表达 `BINDING_IDS`，并具备条件状态流转能力。

- [ ] 在 `QuestionImportBatchStatusEnum` 增加：

```java
BINDING_IDS("BINDING_IDS"),
```

- [ ] 在 `QuestionImportBatchDOMapper` 增加：

```java
int markBindingIds(@Param("id") Long id,
                   @Param("expectedStatus") String expectedStatus,
                   @Param("expectedChunkCount") Integer expectedChunkCount,
                   @Param("expectedRowCount") Integer expectedRowCount);
```

- [ ] 在 `QuestionImportBatchDOMapper.xml` 增加：

```xml
<update id="markBindingIds">
  update t_question_import_batch
  set status = 'BINDING_IDS',
      expected_chunk_count = #{expectedChunkCount,jdbcType=INTEGER},
      updated_time = now()
  where id = #{id,jdbcType=BIGINT}
    and status = #{expectedStatus,jdbcType=VARCHAR}
    and received_chunk_count = #{expectedChunkCount,jdbcType=INTEGER}
    and total_row_count = #{expectedRowCount,jdbcType=INTEGER}
</update>
```

- [ ] 检查 `markReady` 当前是否能传入 expectedStatus。如果已经能传，则后续调用传 `BINDING_IDS`；如果状态机写死 `APPENDING`，只改状态机，不必改 mapper SQL。

验收点：

1. `APPENDING -> BINDING_IDS` 必须是条件更新。
2. 计数不一致时不能进入 `BINDING_IDS`。
3. 不需要新增 DB 字段。

---

### Task 2：先写 finish 首次进入 BINDING_IDS 的失败测试

目标：用测试锁定“finish 不再直接 APPENDING -> READY，而是先进入 BINDING_IDS”。

- [ ] 在 `QuestionImportBatchAppServiceTest` 增加测试：

```java
@Test
void finishImportBatch_appendingBatch_shouldMarkBindingIdsBeforeBindingFormalIds() {
    // given: APPENDING batch, expected count matched
    // when: finishImportBatch
    // then: mapper.markBindingIds(7001L, "APPENDING", 2, 4) called
    // and: final markReady expectedStatus should be "BINDING_IDS"
}
```

- [ ] 运行单测，确认失败原因是生产代码还没有 `markBindingIds` 流程。

建议命令：

```bash
mvn -q -pl eaqb-question-bank/eaqb-question-bank-biz -Dtest=QuestionImportBatchAppServiceTest test
```

预期：

```text
FAIL
Wanted but not invoked: markBindingIds(...)
```

---

### Task 3：实现首次 finish 流程

目标：`APPENDING` 批次 finish 时，先抢占到 `BINDING_IDS`，再绑定 ID，最后从 `BINDING_IDS` 推进 `READY`。

推荐流程：

```text
validateFinishRequest
batch = requireOwnedBatch(batchId)
if status == APPENDING:
    校验 request expected count 与 batch count 一致
    markBindingIds(APPENDING -> BINDING_IDS)
    batch.status = BINDING_IDS
else if status == BINDING_IDS:
    校验 request expected count 与 batch count 一致
else:
    状态非法

bindFormalIdsOrFail(batchId, totalRowCount, BINDING_IDS)
finishReadyInTransaction(batchId, expectedChunkCount, expectedRowCount)
return READY
```

注意：

1. 首次进入 `BINDING_IDS` 前必须校验请求计数。
2. `markBindingIds` 失败要抛 `QUESTION_IMPORT_BATCH_STATUS_ILLEGAL` 或 `QUESTION_IMPORT_BATCH_COUNT_MISMATCH`，具体按现有错误码风格选择。
3. 进入 `BINDING_IDS` 后，绑定阶段失败时应按 `BINDING_IDS` 标记失败，而不是按 `APPENDING`。

---

### Task 4：补 BINDING_IDS 重试测试

目标：finish 重试看到 `BINDING_IDS` 时，不报状态非法，而是继续绑定剩余 `formal_id is null` 的行。

- [ ] 增加测试：

```java
@Test
void finishImportBatch_bindingIdsBatch_shouldResumeBindingOnlyUnboundRows() {
    // given: batch.status = BINDING_IDS, totalRowCount = 4
    // and: selectUnboundIdsByBatchId returns only [503, 504]
    // when: finishImportBatch
    // then: nextQuestionBankEntityIds(2)
    // and: bindFormalIds only called for temp ids 503, 504
    // and: markBindingIds is never called
    // and: final markReady uses expectedStatus BINDING_IDS
}
```

- [ ] 增加测试：

```java
@Test
void finishImportBatch_bindingIdsBatchWithMismatchedExpectedCount_shouldThrow() {
    // given: batch.status = BINDING_IDS, receivedChunkCount=2, totalRowCount=4
    // when: request expectedRowCount=5
    // then: throw QUESTION_IMPORT_BATCH_COUNT_MISMATCH
    // and: no formal id generation
}
```

验收点：

1. `BINDING_IDS` 不是状态非法。
2. `BINDING_IDS` 也不能绕过数量对账。
3. 已绑定 ID 不覆盖，只处理 null 行。

---

### Task 5：调整 formal_id 分批绑定的并发语义

目标：保留每批 1000，但避免并发重试下把“本轮少更新”误判成批次失败。

当前 `bindFormalIdsOrFail` 里如果：

```java
updated != bindings.size()
```

就直接失败。引入 `BINDING_IDS` 后，这个判断需要重新考虑。

原因：

1. 两个 finish 重试可能同时查到同一批 null 行。
2. 两边都取号。
3. 先提交者绑定成功。
4. 后提交者执行 update 时 affected rows 变少，甚至为 0。
5. 这说明行已经被别人绑定，不一定说明数据坏了。

推荐改法：

```text
每轮：
  tempIds = select formal_id is null limit 1000
  if empty: break
  formalIds = next ids
  updated = bindFormalIds(batchId, bindings)
  if updated < 0 or updated > bindings.size: fail
  继续下一轮重新扫描

最终：
  countUnboundFormalId == 0
  countDistinctFormalId == totalRowCount
```

是否允许 `updated == 0`：

- 允许，但不能无限循环。
- 需要保留 `maxIterations`，防止异常情况下死循环。
- 如果持续查到同一批 null 行但 update 一直为 0，最终会触发迭代上限并失败。

补测试：

```java
@Test
void finishImportBatch_concurrentRetryUpdatedLessThanSelected_shouldContinueScanning() {
    // given: first select returns [501, 502], bind returns 0
    // and: second select returns []
    // and: final counts are valid
    // then: should not mark failed only because updated != selected size
}
```

验收点：

1. SQL 仍然带 `formal_id is null`。
2. 不覆盖已绑定 ID。
3. 不把并发导致的少更新直接判死。
4. 最终一致性由 count 校验兜底。

---

### Task 6：把最终校验与 READY 推进放进同一个事务

目标：让 `READY` 只在最终校验通过后原子可见。

新增私有方法，例如：

```java
private FinishTransactionOutcome finishReadyLocked(Long batchId,
                                                   Integer expectedChunkCount,
                                                   Integer expectedRowCount) {
    QuestionImportBatchDO batch = requireOwnedBatchForUpdate(batchId);
    importWorkflowFacade.requireStatus(batch, QuestionImportBatchStatusEnum.BINDING_IDS);

    if (!expectedChunkCount.equals(batch.getReceivedChunkCount())
            || !expectedRowCount.equals(batch.getTotalRowCount())) {
        importWorkflowFacade.markFailedByMapper(batch.getId(), QuestionImportBatchStatusEnum.BINDING_IDS,
                "finish batch count mismatch before ready");
        return FinishTransactionOutcome.failure(new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_COUNT_MISMATCH));
    }

    int tempRowCount = questionImportTempDOMapper.countByBatchId(batch.getId());
    if (tempRowCount != batch.getTotalRowCount()) {
        importWorkflowFacade.markFailedByMapper(batch.getId(), QuestionImportBatchStatusEnum.BINDING_IDS,
                "finish temp row count mismatch before ready");
        return FinishTransactionOutcome.failure(new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_COUNT_MISMATCH));
    }

    if (!formalIdsFullyBound(batch.getId(), batch.getTotalRowCount())) {
        importWorkflowFacade.markFailedByMapper(batch.getId(), QuestionImportBatchStatusEnum.BINDING_IDS,
                "formal id bind check failed before ready");
        return FinishTransactionOutcome.failure(new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_COUNT_MISMATCH));
    }

    importWorkflowFacade.markReadyFromBindingIdsOrThrow(batch.getId(), expectedChunkCount, expectedRowCount);
    return FinishTransactionOutcome.success(...);
}
```

然后 finish 主流程中：

```java
FinishTransactionOutcome outcome = transactionTemplate.execute(status ->
        finishReadyLocked(request.getBatchId(), request.getExpectedChunkCount(), request.getExpectedRowCount()));
```

补测试：

1. `finishImportBatch_finalReadyShouldLockBatchRow`
   - 验证调用 `selectByPrimaryKeyForUpdate`。

2. `finishImportBatch_tempRowCountMismatch_shouldNotReady`
   - `countByBatchId != totalRowCount` 时失败。

3. `finishImportBatch_unboundFormalId_shouldNotReady`
   - `countUnboundFormalId > 0` 时失败。

4. `finishImportBatch_duplicateFormalId_shouldNotReady`
   - `countDistinctFormalId != totalRowCount` 时失败。

验收点：

1. `countByBatchId`、`countUnboundFormalId`、`countDistinctFormalId` 和 `BINDING_IDS -> READY` 在同一个事务回调里。
2. 事务内使用 `selectByPrimaryKeyForUpdate`。
3. `READY` 只从 `BINDING_IDS` 推进。

---

### Task 7：调整恢复查询

目标：系统能识别停在 `BINDING_IDS` 的批次，并让上游有机会继续 finish。

修改 `selectRecoverableByFileIdAndUserId`：

```sql
and status in ('COMMITTED', 'READY', 'BINDING_IDS', 'APPENDING')
order by case status
  when 'COMMITTED' then 1
  when 'READY' then 2
  when 'BINDING_IDS' then 3
  when 'APPENDING' then 4
  else 5
end asc
```

原因：

1. `READY` 比 `BINDING_IDS` 更接近成功，可以直接 commit。
2. `BINDING_IDS` 比 `APPENDING` 更接近成功，因为 finish 数量对账已经通过。
3. 如果不查出 `BINDING_IDS`，中断后的批次可能长期卡住。

补测试：

```java
@Test
void findImportBatchByFile_shouldReturnBindingIdsAsRecoverable() {
    // given: mapper returns BINDING_IDS batch
    // then: response found true, status BINDING_IDS
}
```

验收点：

1. `BINDING_IDS` 是可恢复状态。
2. 上游拿到该状态后，应调用 finish 重试，而不是 commit。

---

### Task 8：处理长期停留 BINDING_IDS 的批次

目标：避免批次永久卡在内部状态。

有两个可选方案。

#### 方案 A：超时失败，保留现场

逻辑：

```text
cleanup scheduler 扫描 updated_time 早于阈值的 BINDING_IDS
markFailedByIds(status = BINDING_IDS, error = "binding ids timed out")
保留 temp 表，等待失败记录保留期后清理
```

优点：

1. 实现简单。
2. 不在 scheduler 里做 RPC 取号。
3. 失败现场清晰。

缺点：

1. 用户需要重新导入。
2. 已绑定的 formal_id 会浪费。

#### 方案 B：后台恢复绑定，失败再超时

逻辑：

```text
scheduler 扫描 BINDING_IDS
尝试继续 formal_id is null 的分批绑定
最终校验通过则推进 READY
多次失败或超时后 FAILED
```

优点：

1. 自动恢复能力更强。
2. 对用户更友好。

缺点：

1. scheduler 需要调用 ID 服务。
2. 逻辑更复杂。
3. 和用户 finish 重试并发时需要共享同一套幂等逻辑。

推荐先做方案 A。

理由：当前目标是修复 READY 语义和半成品状态，不必把自动补偿一起做大。只要用户重试 finish 能恢复，scheduler 超时失败兜底即可。

补测试：

```java
@Test
void cleanupExpiredImportBatches_shouldMarkExpiredBindingIdsFailed() {
    // given: BINDING_IDS batch updated before cutoff
    // when: cleanupExpiredImportBatches
    // then: markFailedByIds or markFailed called with expectedStatus BINDING_IDS
}
```

验收点：

1. `BINDING_IDS` 不会永久停留。
2. 清理逻辑不会误删 `READY` 或 `COMMITTED`。

---

### Task 9：更新文档和注释

目标：让后续阅读者理解 `BINDING_IDS` 为什么存在。

需要更新：

1. `docs/excel-import/08-导入流程代码阅读导航.md`
   - 增加 `BINDING_IDS` 状态说明。
   - 更新恢复优先级。

2. `docs/excel-import/07-commit响应超时与导入幂等必要性.md`
   - 如果文中只列 `APPENDING / READY / COMMITTED`，补充 `BINDING_IDS`。

3. `QuestionImportBatchStatusEnum` 注释
   - 说明 `BINDING_IDS` 是 finish 内部恢复状态。

注意：

1. 文档更新不应改变业务语义。
2. 不要把 `BINDING_IDS` 描述成用户可操作状态。
3. 不要说它可以 commit。

---

## 5. TDD 执行清单

建议按以下顺序写测试和实现：

- [ ] 测试：`APPENDING` finish 会先调用 `markBindingIds`。
- [ ] 实现：枚举、mapper、state machine 支持 `BINDING_IDS`。
- [ ] 测试：`BINDING_IDS` finish 重试能继续绑定 null formal_id。
- [ ] 实现：finish 主流程支持 `BINDING_IDS`。
- [ ] 测试：`BINDING_IDS` 下 expected count 不一致会失败。
- [ ] 实现：重试入参计数校验。
- [ ] 测试：并发导致本轮 bind affected rows 少于 selected rows 时不直接失败。
- [ ] 实现：分批绑定改为最终 count 兜底。
- [ ] 测试：最终 READY 前会锁 batch 行。
- [ ] 实现：最终校验和 `BINDING_IDS -> READY` 放进 `TransactionTemplate`。
- [ ] 测试：temp 行数不一致不进 READY。
- [ ] 测试：存在 unbound formal_id 不进 READY。
- [ ] 测试：formal_id distinct 数不等于 totalRowCount 不进 READY。
- [ ] 测试：recover 查询能返回 `BINDING_IDS`。
- [ ] 实现：recover 查询优先级。
- [ ] 测试：cleanup 能处理超时 `BINDING_IDS`。
- [ ] 实现：cleanup 超时失败。
- [ ] 跑 `QuestionImportBatchAppServiceTest`。
- [ ] 跑 `QuestionImportBatchCleanupSchedulerTest`。
- [ ] 跑 question-bank-biz 模块测试。

---

## 6. 关键测试用例清单

### 6.1 finish 正常路径

```text
given APPENDING batch:
  receivedChunkCount = 2
  totalRowCount = 4
  expectedChunkCount = 2
  expectedRowCount = 4
  temp rows all unbound

when finish

then:
  APPENDING -> BINDING_IDS
  每批最多 1000 取号
  bind formal_id
  final transaction lock batch
  countByBatchId == 4
  countUnboundFormalId == 0
  countDistinctFormalId == 4
  BINDING_IDS -> READY
```

### 6.2 finish 重试恢复

```text
given BINDING_IDS batch:
  totalRowCount = 4
  temp row 1/2 already has formal_id
  temp row 3/4 formal_id is null

when finish retry

then:
  not call markBindingIds
  select only formal_id is null rows
  generate 2 formal ids
  bind row 3/4
  final transaction mark READY
```

### 6.3 重试入参不一致

```text
given BINDING_IDS batch:
  receivedChunkCount = 2
  totalRowCount = 4

when finish retry with expectedRowCount = 5

then:
  throw QUESTION_IMPORT_BATCH_COUNT_MISMATCH
  not generate formal ids
  not mark READY
```

### 6.4 并发绑定少更新

```text
given selectUnboundIds returns [501, 502]
and next ids returns [9001, 9002]
and bindFormalIds returns 0 because another request already bound them
and next selectUnboundIds returns empty
and final count checks pass

then:
  finish should continue to final verification
  not fail only because updated != bindings.size()
```

### 6.5 最终事务校验失败

```text
case 1:
  countByBatchId != totalRowCount
  should fail and not READY

case 2:
  countUnboundFormalId > 0
  should fail and not READY

case 3:
  countDistinctFormalId != totalRowCount
  should fail and not READY
```

### 6.6 recover 查询

```text
given same fileId/userId has:
  one APPENDING
  one BINDING_IDS

then:
  recover should choose BINDING_IDS before APPENDING
```

### 6.7 cleanup 超时

```text
given BINDING_IDS batch updated_time older than threshold

when cleanup scheduler runs

then:
  mark FAILED with expectedStatus = BINDING_IDS
```

---

## 7. 设计取舍

### 7.1 为什么加状态，而不是直接给 finish 加大事务

不能把整个 finish 都放进一个长事务。

原因：

1. finish 中间要调用分布式 ID 服务。
2. 大批量绑定可能循环多次。
3. 长事务会持有连接和锁，失败成本高。
4. RPC 在事务内会放大不确定性。

所以更合理的是：

```text
短事务/条件更新：APPENDING -> BINDING_IDS
无长事务循环：分批绑定 formal_id
短事务/锁内校验：BINDING_IDS -> READY
```

### 7.2 为什么不一次性全量取号

不能为了原子性改成一次性取所有 formal_id。

原因：

1. Excel 行数可能较多。
2. RPC 响应体过大。
3. 内存压力更高。
4. 单次 SQL CASE WHEN 更新太大。
5. 失败重试成本更高。

继续保留每批 1000 更符合当前实现和稳定性要求。

### 7.3 为什么最终校验还要 countDistinctFormalId

`countUnboundFormalId == 0` 只能说明每行都有 ID。

还需要：

```text
countDistinctFormalId == totalRowCount
```

才能说明没有重复 ID。

否则两个 temp 行可能绑定到同一个 formal_id，commit 后正式题目 ID 会冲突或污染。

---

## 8. 风险与注意事项

1. `BINDING_IDS` 是内部状态，但接口响应可能会暴露 status 字符串；上游需要知道它代表“继续 finish”，不是“可以 commit”。
2. cleanup 如果直接把超时 `BINDING_IDS` 标为 `FAILED`，可能让仍在慢速执行的 finish 失败；需要合理设置超时时间。
3. `markFailedByMapper` 在事务内调用会随事务回滚；如果希望失败状态独立落库，要考虑 writer，但要避免和最终事务语义冲突。
4. 并发重试会浪费少量 formal_id，这是可接受成本；不能为了避免浪费而覆盖已有绑定。
5. 如果 `formal_id` 在 temp 表没有唯一约束，最终 `countDistinctFormalId` 是进入 `READY` 前的关键防线。
6. 如果后续要做后台自动恢复，建议复用 service 中同一套绑定逻辑，不要在 scheduler 里复制一份。

---

## 9. 推荐验证命令

优先跑聚焦测试：

```bash
mvn -q -pl eaqb-question-bank/eaqb-question-bank-biz -Dtest=QuestionImportBatchAppServiceTest test
```

再跑清理任务测试：

```bash
mvn -q -pl eaqb-question-bank/eaqb-question-bank-biz -Dtest=QuestionImportBatchCleanupSchedulerTest test
```

最后跑模块测试：

```bash
mvn -q -pl eaqb-question-bank/eaqb-question-bank-biz test
```

如果模块测试依赖外部中间件或环境导致失败，需要记录失败原因，并至少保证上述两个聚焦测试通过。

---

## 10. 最小可交付范围

本次修复的最小可交付范围：

1. 新增 `BINDING_IDS`。
2. finish 首次执行：`APPENDING -> BINDING_IDS`。
3. finish 重试：识别 `BINDING_IDS` 并继续补齐 null formal_id。
4. 分批取号/绑定仍然每批最多 1000。
5. 已绑定 formal_id 不覆盖。
6. 最终事务内锁 batch，校验 temp 行数、未绑定数、distinct formal_id。
7. `BINDING_IDS -> READY` 与最终校验原子提交。
8. recover 查询能识别 `BINDING_IDS`。
9. cleanup 对长期 `BINDING_IDS` 有兜底。
10. 单测覆盖正常、重试、并发少更新、最终校验失败、恢复查询、超时清理。

暂不做：

1. 后台自动补齐 `BINDING_IDS`。
2. 新增复杂重试次数表。
3. 一次性全量取号。
4. 改造正式题目 commit 主流程。

