# finish 阶段 formal_id 绑定事务边界说明

## 1. 结论

`QuestionImportBatchAppService.finishImportBatch` 当前的事务边界确实偏弱。

现有流程是在无整体本地事务的情况下，先分批给 `t_question_import_temp.formal_id` 绑定正式题目 ID，然后再把导入批次从 `APPENDING` 推进到 `READY`。如果绑定过程中发生异常、进程中断，或者多个 finish 请求并发执行，就可能留下：

```text
batch.status = APPENDING
部分 temp 行 formal_id 已绑定
部分 temp 行 formal_id 仍为 null
```

这类状态不是 `READY`，但临时表已经被部分修改，属于 finish 阶段半成品。

## 2. 当前风险点

当前实现的关键问题不是“分批取号”本身，而是缺少一个能表达“正在绑定 ID”的内部状态，以及缺少最终推进 `READY` 前的锁内事务边界。

主要风险包括：

1. `finishImportBatch` 没有整体 `@Transactional`，分批绑定会逐批提交。
2. `requireOwnedBatch` 使用普通查询，没有锁住 batch 行。
3. `formal_id` 绑定完成校验和 `APPENDING -> READY` 不在同一个本地事务内。
4. 并发 finish 时，多个请求可能同时基于 `APPENDING` 快照执行绑定逻辑。
5. `bindFormalIds` 虽然带 `formal_id is null`，不会覆盖已绑定值，但并发或中断后仍可能造成“部分绑定 + 非 READY”的中间状态。

因此，`READY` 语义目前不够硬：它依赖前置步骤都顺利完成，但数据库层面没有把“最终校验”和“状态推进”收束成一个原子提交。

## 3. 推荐方案

引入内部状态 `BINDING_IDS`：

```text
APPENDING -> BINDING_IDS -> READY -> COMMITTED
```

`BINDING_IDS` 只表示 finish 已经完成数量对账，正在为临时行绑定正式题目 ID。它不是对外可提交状态，`commitImportBatch` 仍然只接受 `READY` 或幂等返回 `COMMITTED`。

推荐流程：

1. finish 请求先校验参数和批次归属。
2. 如果 batch 是 `APPENDING`，先校验 `expectedChunkCount / expectedRowCount` 与 batch 累计值一致。
3. 用条件更新把 `APPENDING -> BINDING_IDS`，抢占 finish 权。
4. 如果 batch 已经是 `BINDING_IDS`，允许重试继续处理，但仍必须校验 finish 入参与 batch 已持久化的 `receivedChunkCount / totalRowCount` 一致；不一致时拒绝，不能绕过数量对账。
5. 继续保留每批 1000 的取号和绑定逻辑。
6. 每轮只查询 `formal_id is null` 的 temp 行，已绑定行不覆盖。
7. 所有 null formal_id 处理完后，进入一个本地事务。
8. 事务内 `select ... for update` 锁住 batch 行。
9. 锁内校验 batch 仍为 `BINDING_IDS`，校验临时表总行数等于 `totalRowCount`。
10. 锁内校验 `countUnboundFormalId == 0`。
11. 锁内校验 `countDistinctFormalId == totalRowCount`。
12. 锁内执行 `BINDING_IDS -> READY`。

这里的重试语义有一个前提：`BINDING_IDS` 不是“跳过 finish 对账”，而是“对账已经按 batch 当前累计值完成，正在补齐 ID 绑定”。因此重试请求仍要证明自己对应同一个批次快照。实现上可以继续使用请求中的 `expectedChunkCount / expectedRowCount` 做一致性校验，也可以在内部恢复任务中完全不信任外部入参，只基于 batch 持久化计数推进。

## 4. 为什么不做一次性全量取号

不建议把修复改成一次性全量取号。

原因是大 Excel 导入时，行数可能较多。一次性取号会放大 RPC 响应体、内存占用、单次 SQL 更新体积和失败重试成本。当前每批 1000 的思路更稳，问题只在状态和事务边界，不在分页模型本身。

所以修复应保留：

```text
select formal_id is null limit 1000
nextQuestionBankEntityIds(size)
bindFormalIds(batchId, bindings)
```

这样中断后重试也很自然：继续处理剩余 `formal_id is null` 的行即可。

并发重试下还需要明确行级幂等边界：

1. `bindFormalIds` 必须按 `batch_id + temp_id + formal_id is null` 条件更新。
2. 已绑定行不能被覆盖。
3. 如果两个请求选中了同一批 null 行，后提交的一方可能只更新到部分行，甚至 0 行；这不应直接代表批次失败。
4. 完成判断只能依赖后续重新扫描和最终 `countUnboundFormalId / countDistinctFormalId` 校验，不能只依赖“本轮取号数量 == 本轮更新数量”。
5. 被并发浪费掉的正式 ID 可以接受，但不能污染 temp 表，也不能导致错误进入 `READY`。

## 5. READY 的硬语义

修复后，`READY` 应只表示：

1. finish 上报数量与 batch 累计数量已经对账通过。
2. 临时表实际行数与 batch.totalRowCount 一致。
3. 所有 temp 行都已经绑定 `formal_id`。
4. `formal_id` 去重数量等于 `totalRowCount`，不存在重复绑定。
5. 上述最终校验和 `BINDING_IDS -> READY` 在同一个本地事务内完成。
6. 最终事务内锁住 batch 行，保证 `READY` 只在最终校验完成后原子可见。

这样 `READY` 才能作为 commit 阶段的可靠前置条件：只要 batch 进入 `READY`，commit 就可以相信临时表已经完整、可转正。

## 6. 实现要点

需要关注的代码点：

- `QuestionImportBatchStatusEnum`：增加 `BINDING_IDS`。
- `QuestionImportBatchDOMapper`：增加 `markBindingIds`，并支持 `BINDING_IDS -> READY` 的条件更新。
- `QuestionImportBatchAppService.finishImportBatch`：改为先抢占到 `BINDING_IDS`，再分批绑定，最后进入锁内事务推进 `READY`。
- `selectRecoverableByFileIdAndUserId`：必须考虑 `BINDING_IDS` 的恢复路径。否则进程在 `APPENDING -> BINDING_IDS` 后中断时，批次会长期卡住，只是把半成品状态从 `APPENDING` 迁移到了 `BINDING_IDS`。
- 恢复策略：推荐把 `BINDING_IDS` 纳入可恢复候选，优先级低于 `READY`、高于 `APPENDING`，并由 finish 重试继续补齐绑定；如果不走用户请求恢复，也必须有后台任务能继续绑定或超时失败。
- `QuestionImportBatchCleanupScheduler`：需要考虑卡在 `BINDING_IDS` 的超时批次，避免长期占用恢复入口。
- 单测覆盖：
  - `APPENDING -> BINDING_IDS -> READY` 正常路径。
  - 已经处于 `BINDING_IDS` 时，finish 入参仍需与 batch 计数一致。
  - 已经处于 `BINDING_IDS` 时，只绑定 `formal_id is null` 的行。
  - 并发绑定同一批 temp 行时，不覆盖已有 `formal_id`，并能继续扫描剩余未绑定行。
  - 最终事务内行数不一致时不进入 `READY`。
  - `countUnboundFormalId > 0` 时不进入 `READY`。
  - `countDistinctFormalId != totalRowCount` 时不进入 `READY`。
  - 并发或重复 finish 不覆盖已有 `formal_id`。
