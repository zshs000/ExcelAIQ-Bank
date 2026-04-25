# 2026-04-25 outbox管理员人工重试说明

本次改动为题目 outbox 链路补齐了管理员人工补偿入口，支持管理员查看达到最大重试次数后进入 `FAILED` 的 outbox 事件，并手动恢复整条派发链路，重新交给调度器继续派发。

## 实现效果

- 新增管理员失败 outbox 列表接口：`GET /question/admin/outbox/failed`
- 新增管理员人工重试接口：`POST /question/admin/outbox/{eventId}/retry`
- 人工重试时同步恢复三段状态：
  - `question: sourceQuestionStatus -> DISPATCHING`
  - `task: FAILED -> PENDING_DISPATCH`
  - `outbox: FAILED -> RETRYABLE`
- 保留 `dispatchRetryCount`、`lastError`、`lastErrorTime`
- 将 `nextRetryTime` 置为当前时间，交由调度器立即重新扫描
- 为 `/question/admin/**` 补齐网关 `admin` 角色校验
- 为管理员人工重试失败增加独立事务日志，主事务回滚时日志仍可落库

## 关键实现文件

### 管理员接口与应用服务

- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/controller/QuestionAdminController.java`
  - 提供管理员失败 outbox 查询与人工重试入口
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionOutboxAdminAppService.java`
  - 编排人工重试逻辑
  - 统一恢复 `question / task / outbox` 三段状态
  - 主链路失败时抛异常触发事务回滚

### 人工重试失败日志

- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionOutboxAdminRetryLogWriter.java`
  - 使用 `REQUIRES_NEW` 独立事务记录管理员人工重试失败日志
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/dataobject/QuestionOutboxAdminRetryLogDO.java`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/mapper/QuestionOutboxAdminRetryLogDOMapper.java`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/resources/mapper/QuestionOutboxAdminRetryLogDOMapper.xml`
  - 对应人工重试失败日志表的落库模型与 SQL

### 出参模型与 outbox 查询能力

- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/model/vo/FailedOutboxEventVO.java`
  - 封装失败 outbox 列表返回结构
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/mapper/QuestionOutboxEventDOMapper.java`
- `eaqb-question-bank/eaqb-question-bank-biz/src/main/resources/mapper/QuestionOutboxEventDOMapper.xml`
  - 补充 `selectByPrimaryKey`
  - 复用现有失败更新 SQL 完成 `FAILED -> RETRYABLE`

### 网关鉴权

- `eaqb-gateway/src/main/java/com/zhoushuo/eaqb/gateway/auth/SaTokenConfigure.java`
  - 增加 `/question/admin/**` 的 `admin` 角色校验

### 测试与 SQL

- `eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionOutboxAdminAppServiceTest.java`
  - 覆盖失败列表查询、人工重试成功、非法状态拦截、主事务失败回滚、独立事务日志声明
- `docs/sql/2026-04-25-add-question-outbox-admin-retry-log.sql`
  - 新增管理员人工重试失败日志表

## 验证结果

- `eaqb-question-bank-biz` 定向测试通过
- `eaqb-question-bank-biz` 模块全量测试通过
- `eaqb-gateway` 编译通过
