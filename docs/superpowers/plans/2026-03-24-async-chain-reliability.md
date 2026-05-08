# Async Chain Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the question AI async chain so send, callback, retry, and review rely on explicit task/outbox/inbox state instead of fragile timing windows on `t_question_bank.process_status`.

**Architecture:** Keep `t_question_bank` as the business-facing aggregate, then add three process-side persistence units: `question_process_task` for one async attempt, `question_outbox_event` for reliable dispatch, and `question_callback_inbox` for callback idempotency. Producer-side send becomes "write DB first, dispatch later"; consumer-side callback handling becomes "identify task first, then commit question/task/review changes in one transaction".

**Tech Stack:** Spring Boot, MyBatis XML mappers, RocketMQ, JUnit 5, Mockito, Maven.

---

## File Map

**Create:**
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/dataobject/QuestionProcessTaskDO.java`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/dataobject/QuestionOutboxEventDO.java`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/dataobject/QuestionCallbackInboxDO.java`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/mapper/QuestionProcessTaskDOMapper.java`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/mapper/QuestionOutboxEventDOMapper.java`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/mapper/QuestionCallbackInboxDOMapper.java`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/enums/QuestionProcessTaskStatusEnum.java`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/enums/OutboxEventStatusEnum.java`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/enums/CallbackInboxStatusEnum.java`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/model/QuestionDispatchMessage.java`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/QuestionDispatchService.java`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionDispatchServiceImpl.java`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/resources/mapper/QuestionProcessTaskDOMapper.xml`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/resources/mapper/QuestionOutboxEventDOMapper.xml`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/resources/mapper/QuestionCallbackInboxDOMapper.xml`

**Modify:**
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionServiceImpl.java`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/QuestionService.java`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/model/QuestionMessage.java`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/model/AIProcessResultMessage.java`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/consumer/AIProcessResultConsumer.java`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/mapper/QuestionDOMapper.java`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/resources/mapper/QuestionDOMapper.xml`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/constant/MQConstants.java`
- `eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionServiceImplTest.java`
- `eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/consumer/AIProcessResultConsumerTest.java` (create if absent)
- `eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionDispatchServiceImplTest.java`

**Docs / SQL placeholders:**
- `eaqb-question-bank/题目审核链路重构总结.md`
- `eaqb-question-bank/题目状态流转图.md`
- `eaqb-question-bank/AI生成与校验契约_v1.md`

---

### Task 1: Lock Message Identity Model

**Files:**
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/model/QuestionMessage.java`
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/model/AIProcessResultMessage.java`
- Test: `eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

Add tests asserting dispatch messages include `taskId`, `attemptNo`, and callback messages can resolve `taskId` and a stable callback key.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=QuestionServiceImplTest test`
Expected: FAIL because message model has no task identity fields.

- [ ] **Step 3: Write minimal implementation**

Add fields to dispatch/callback message models:
- `taskId`
- `attemptNo`
- `callbackKey`

Keep old fields for compatibility, but make the new fields first-class.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=QuestionServiceImplTest test`
Expected: PASS for the new message identity assertions.

- [ ] **Step 5: Commit**

```bash
git add eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/model/QuestionMessage.java eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/model/AIProcessResultMessage.java eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionServiceImplTest.java
git commit -m "feat: add task identity to ai messages"
```

### Task 2: Add Process Persistence Units

**Files:**
- Create: `.../QuestionProcessTaskDO.java`
- Create: `.../QuestionOutboxEventDO.java`
- Create: `.../QuestionCallbackInboxDO.java`
- Create: `.../QuestionProcessTaskDOMapper.java`
- Create: `.../QuestionOutboxEventDOMapper.java`
- Create: `.../QuestionCallbackInboxDOMapper.java`
- Create: `.../QuestionProcessTaskDOMapper.xml`
- Create: `.../QuestionOutboxEventDOMapper.xml`
- Create: `.../QuestionCallbackInboxDOMapper.xml`
- Create: `.../QuestionProcessTaskStatusEnum.java`
- Create: `.../OutboxEventStatusEnum.java`
- Create: `.../CallbackInboxStatusEnum.java`
- Test: `eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionDispatchServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

Create tests for:
- creating a task with `PENDING_DISPATCH`
- creating an outbox event with `NEW`
- inserting an inbox record with unique callback key semantics

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=QuestionDispatchServiceImplTest test`
Expected: FAIL because the new persistence units do not exist.

- [ ] **Step 3: Write minimal implementation**

Create the DOs, enums, mapper interfaces, and XML with only the fields and methods required by the tests:
- insert/select/update-by-status
- select latest active task by question id
- select by callback key

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=QuestionDispatchServiceImplTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/dataobject eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/mapper eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/enums eaqb-question-bank/eaqb-question-bank-biz/src/main/resources/mapper eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionDispatchServiceImplTest.java
git commit -m "feat: add async task outbox and inbox persistence"
```

### Task 3: Move Send Logic to DB-First Dispatch Service

**Files:**
- Create: `.../QuestionDispatchService.java`
- Create: `.../QuestionDispatchServiceImpl.java`
- Modify: `.../QuestionServiceImpl.java`
- Modify: `.../QuestionService.java`
- Test: `.../QuestionDispatchServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

Add tests that `sendQuestionsToQueue` only:
- validates ownership/mode
- moves question to `DISPATCHING`
- inserts task
- inserts outbox
- does not call RocketMQ directly

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=QuestionServiceImplTest,QuestionDispatchServiceImplTest test`
Expected: FAIL because send path still talks to RocketMQ inline.

- [ ] **Step 3: Write minimal implementation**

Refactor send logic:
- `QuestionServiceImpl` keeps API validation and delegates
- `QuestionDispatchServiceImpl` opens one local transaction per eligible question:
  - `question: WAITING -> DISPATCHING`
  - insert `question_process_task`
  - insert `question_outbox_event`

Leave the response shape unchanged where possible.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=QuestionServiceImplTest,QuestionDispatchServiceImplTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/service/impl
git commit -m "feat: refactor question send flow to outbox dispatch"
```

### Task 4: Dispatch Outbox Events Reliably

**Files:**
- Modify: `.../QuestionDispatchServiceImpl.java`
- Modify: `.../QuestionProcessTaskDOMapper.java`
- Modify: `.../QuestionOutboxEventDOMapper.java`
- Modify: `.../QuestionDOMapper.java`
- Modify: `.../QuestionDOMapper.xml`
- Test: `.../QuestionDispatchServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

Add tests for a dispatcher method like `dispatchPendingOutboxEvents()`:
- send MQ success updates outbox to `SENT`, task to `DISPATCHED`, question to `PROCESSING`
- MQ failure leaves outbox retriable and question/task unchanged or explicitly marked dispatch-failed

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=QuestionDispatchServiceImplTest test`
Expected: FAIL because there is no dispatcher and no post-send status sync.

- [ ] **Step 3: Write minimal implementation**

Implement a method that:
- selects `NEW`/`RETRYABLE` outbox events
- sends RocketMQ message with `taskId`
- in one local transaction updates:
  - outbox status
  - task dispatch status
  - question `DISPATCHING -> PROCESSING`

This can be called inline first; scheduling can come later.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=QuestionDispatchServiceImplTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/mapper eaqb-question-bank/eaqb-question-bank-biz/src/main/resources/mapper eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionDispatchServiceImplTest.java
git commit -m "feat: dispatch outbox events and sync question state"
```

### Task 5: Make Callback Consumption Task-Driven and Idempotent

**Files:**
- Modify: `.../AIProcessResultConsumer.java`
- Modify: `.../QuestionServiceImpl.java`
- Modify: `.../QuestionProcessTaskDOMapper.java`
- Modify: `.../QuestionCallbackInboxDOMapper.java`
- Test: `.../AIProcessResultConsumerTest.java`
- Test: `.../QuestionServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

Add tests for:
- duplicate callback key is ignored idempotently
- callback finds task by `taskId` even if question status is still `DISPATCHING`
- processing exception is rethrown so RocketMQ can retry

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AIProcessResultConsumerTest,QuestionServiceImplTest test`
Expected: FAIL because callback flow still keys off question status and has no inbox.

- [ ] **Step 3: Write minimal implementation**

Change callback processing to:
- insert/select inbox by callback key
- load active task by task id
- reject stale task or duplicate callback safely
- call service method that updates question/task/review artifacts in one transaction
- rethrow processing failure

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=AIProcessResultConsumerTest,QuestionServiceImplTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/consumer eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/mapper eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/consumer eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/service/impl
git commit -m "feat: make ai callback handling task-driven and idempotent"
```

### Task 6: Commit Callback State Atomically for Generate and Validate

**Files:**
- Modify: `.../QuestionServiceImpl.java`
- Modify: `.../QuestionValidationRecordDOMapper.java`
- Test: `.../QuestionServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

Add tests that callback success does all-or-nothing:
- `GENERATE`: question answer + question status + task success
- `VALIDATE`: validation record + question status + task success
- failure callback: question failure status + task failure status + fail reason persisted

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=QuestionServiceImplTest test`
Expected: FAIL because callback updates are not task-transaction-centric and failure reason is not persisted.

- [ ] **Step 3: Write minimal implementation**

Add transactional internal methods:
- `handleGenerateTaskSuccess(...)`
- `handleValidateTaskSuccess(...)`
- `handleTaskFailure(...)`

Persist task failure reason and callback inbox consume status.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=QuestionServiceImplTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionServiceImpl.java eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/mapper/QuestionValidationRecordDOMapper.java eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionServiceImplTest.java
git commit -m "feat: commit callback result atomically by task"
```

### Task 7: Verify End-to-End and Refresh Docs

**Files:**
- Modify: `eaqb-question-bank/题目审核链路重构总结.md`
- Modify: `eaqb-question-bank/题目状态流转图.md`
- Modify: `eaqb-question-bank/AI生成与校验契约_v1.md`
- Test: module suite

- [ ] **Step 1: Write or update final tests**

Add any remaining integration-style service tests for:
- outbox dispatch path
- callback duplicate path
- validate review path after callback

- [ ] **Step 2: Run targeted tests**

Run: `mvn -q -Dtest=QuestionServiceImplTest,QuestionDispatchServiceImplTest,AIProcessResultConsumerTest test`
Expected: PASS

- [ ] **Step 3: Run full module tests**

Run: `mvn -q test`
Expected: PASS

- [ ] **Step 4: Refresh docs**

Update the markdown docs to reflect:
- `DISPATCHING` as dispatch-phase state
- task/outbox/inbox architecture
- callback no longer depends solely on question `PROCESSING`

- [ ] **Step 5: Commit**

```bash
git add eaqb-question-bank/题目审核链路重构总结.md eaqb-question-bank/题目状态流转图.md eaqb-question-bank/AI生成与校验契约_v1.md eaqb-question-bank/eaqb-question-bank-biz/src/test eaqb-question-bank/eaqb-question-bank-biz/src/main
git commit -m "feat: complete reliable async chain refactor"
```

---

## Notes

- Keep database DDL out of scope for this coding pass if the user still wants to apply SQL manually, but every new DO/mapper must clearly imply the required table/column contract.
- Do not remove the existing `question_validation_record` direction; integrate it into the task-driven callback model.
- Do not rely on delayed retry as the correctness mechanism. Delayed retry is acceptable later as a resilience enhancement, not as the primary contract.
- Preserve the current user-facing review semantics:
  - `GENERATE`: `APPLY_AI`, `REJECT`
  - `VALIDATE`: `KEEP_ORIGINAL`, `APPLY_AI`, `REJECT`
