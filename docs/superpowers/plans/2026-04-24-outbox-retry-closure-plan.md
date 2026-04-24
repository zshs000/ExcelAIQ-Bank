# Outbox Retry Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让题目派发 outbox 具备“退避重试 + 最大重试上限 + 失败终态 + 错误明细”的闭环能力。

**Architecture:** 保持现有 `question / task / outbox` 职责边界不变，只增强 `question_outbox_event` 的生命周期。发送成功继续推进 `SENT`；发送失败时根据重试次数推进到 `RETRYABLE` 或 `FAILED`，scheduler 只扫描到期可重试的 outbox。

**Tech Stack:** Java 17, Spring Boot, MyBatis XML, JUnit 5, Mockito, Maven

---

### Task 1: 先写失败测试锁定新行为

**Files:**
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionDispatchServiceImplTest.java`
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionOutboxDispatchSchedulerTest.java`

- [ ] **Step 1: 写出失败/到顶/调度扫描的新测试**
- [ ] **Step 2: 运行定向测试，确认因缺少实现而失败**

### Task 2: 扩展 outbox 数据模型与 mapper

**Files:**
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/enums/OutboxEventStatusEnum.java`
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/dataobject/QuestionOutboxEventDO.java`
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/mapper/QuestionOutboxEventDOMapper.java`
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/main/resources/mapper/QuestionOutboxEventDOMapper.xml`
- Create: `docs/sql/2026-04-24-add-outbox-retry-closure.sql`

- [ ] **Step 1: 增加 `FAILED` 状态与重试元数据字段**
- [ ] **Step 2: 增加“可扫描 outbox 查询”和“失败更新”SQL**

### Task 3: 实现派发失败闭环

**Files:**
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionDispatchServiceImpl.java`
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/main/resources/config/application.yml`

- [ ] **Step 1: 注入最大重试/退避配置**
- [ ] **Step 2: 发送失败时区分 `RETRYABLE` 与 `FAILED`**
- [ ] **Step 3: 最终失败时同步 `task -> FAILED`、`question -> sourceQuestionStatus`**

### Task 4: 实现到期扫描并完成验证

**Files:**
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionOutboxDispatchScheduler.java`

- [ ] **Step 1: scheduler 改为只扫到期的 `NEW/RETRYABLE`**
- [ ] **Step 2: 运行定向测试验证通过**
- [ ] **Step 3: 如有必要补充文档注释，保证状态语义一致**
