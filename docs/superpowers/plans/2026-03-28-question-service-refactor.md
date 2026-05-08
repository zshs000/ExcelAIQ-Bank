# Question Service Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `QuestionServiceImpl` 从 1000+ 行上帝类拆成薄门面和按链路分工的应用服务，同时保持 `QuestionService` 对外契约不变。

**Architecture:** 保留 `QuestionService` 与 `QuestionServiceImpl` 作为兼容 facade，controller 和 MQ consumer 暂不改注入目标。将原实现按 CRUD、派发、审核、回包消费、规则支持拆到独立 Spring Service 中，逐步迁移逻辑并用现有行为测试兜底。

**Tech Stack:** Spring Boot, MyBatis, RocketMQ, JUnit 5, Mockito

---

### Task 1: 固化拆分边界

**Files:**
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionServiceImpl.java`
- Create: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionWorkflowSupport.java`

- [ ] 梳理 `QuestionServiceImpl` 中的通用规则与常量。
- [ ] 抽出 `QuestionWorkflowSupport`，收纳 `mode/decision/status` 归一化、用户上下文读取、通用异常构造等支持逻辑。
- [ ] 让原类改为依赖支持类，而不是继续堆私有 helper。

### Task 2: 拆出 AI 回包消费服务

**Files:**
- Create: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionCallbackAppService.java`
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionServiceImpl.java`
- Test: `eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionServiceImplTest.java`

- [ ] 先让现有回包行为测试保持可运行。
- [ ] 将成功/失败回包、inbox 幂等、task 对账、validation_record 创建与 task 状态推进迁移到 `QuestionCallbackAppService`。
- [ ] 让 facade 仅委托 `batchUpdateSuccessQuestions` 与 `batchUpdateFailedQuestions`。

### Task 3: 拆出审核服务

**Files:**
- Create: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionReviewAppService.java`
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionServiceImpl.java`
- Test: `eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionServiceImplTest.java`

- [ ] 保留 `reviewQuestion` 外部接口不变。
- [ ] 迁移 `GENERATE/VALIDATE` 审核分支、校验记录联动、状态回退与失败回滚逻辑。
- [ ] 让 facade 只保留转发。

### Task 4: 拆出派发服务

**Files:**
- Create: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionDispatchAppService.java`
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionServiceImpl.java`
- Test: `eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionServiceImplTest.java`

- [ ] 迁移 `sendQuestionsToQueue`、mock 派发、状态锁定、返回统计拼装。
- [ ] 保持 outbox 语义修正后的返回结果不变。
- [ ] 修正测试中对反射字段的依赖，使其指向新服务实例。

### Task 5: 拆出 CRUD 服务并瘦身 facade

**Files:**
- Create: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionCrudService.java`
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionServiceImpl.java`
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionServiceImplTest.java`

- [ ] 迁移创建、批量导入、分页、删除、更新。
- [ ] 删除 facade 中残余 mapper / MQ / 规则细节依赖。
- [ ] 确认 `QuestionServiceImpl` 最终只保留注入与方法转发。

### Task 6: 验证与收尾

**Files:**
- Test: `eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionServiceImplTest.java`
- Test: `eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/consumer/AIProcessResultConsumerTest.java`

- [ ] 运行 question-bank 相关单测，确认 facade 与 MQ consumer 链路不回归。
- [ ] 运行模块编译，确认 Spring 依赖注入与签名编译通过。
- [ ] 总结新边界与后续可选的 controller/consumer 直连专用服务演进路径。
