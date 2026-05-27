# AGENTS.md - Excel AI Q-Bank

## 1. 项目定位

Excel AI Q-Bank 是基于 Spring Cloud Alibaba 的智能题库系统。核心链路是：用户上传 Excel，Excel 解析服务分块解析，题库服务接收临时题目并提交为正式题目，后续通过 RocketMQ 驱动 AI 处理。

技术栈：Java 17、Spring Boot 3、Spring Cloud Alibaba、MySQL、Redis、Nacos、RocketMQ、MinIO/OSS。

主要模块：

- `eaqb-gateway`：API 网关。
- `eaqb-auth`：认证服务。
- `eaqb-user`：用户服务。
- `eaqb-question-bank`：题库核心服务。
- `eaqb-excel-parser`：Excel 校验、解析、导入编排服务。
- `eaqb-oss`：对象存储服务。
- `eaqb-distributed-id-generator`：分布式 ID 服务。
- `eaqb-framework`：公共框架和 starter。

## 2. 常用命令

默认开发环境是 Windows，终端通常是 PowerShell。编写命令时优先使用 Windows/PowerShell 可直接运行的形式；不要默认使用 Linux shell 语法、环境变量写法、路径分隔符或命令组合方式。Maven 参数里如果包含点号，建议用引号包起来，例如 `'-Dsurefire.failIfNoSpecifiedTests=false'`。

```powershell
# 全量编译，跳过测试
mvn clean install -DskipTests

# 单模块编译
mvn clean install -pl eaqb-question-bank/eaqb-question-bank-biz -am -DskipTests

# 全量测试
mvn test

# 单模块测试
mvn test -pl eaqb-question-bank/eaqb-question-bank-biz

# 单测试类或单用例
mvn -q -pl eaqb-question-bank/eaqb-question-bank-biz -am -Dtest=QuestionImportBatchAppServiceTest test
```

服务启动通常依赖 MySQL、Redis、Nacos、RocketMQ 和对象存储。环境配置以 Nacos 为准；本地配置只保留启动所需的 bootstrap/兜底项。

## 3. 代码边界

1. **API / Biz 分离**：每个微服务按 `xxx-api` 和 `xxx-biz` 拆分。其他服务只能依赖 API 包，通过 Feign 调用，不要跨模块直接依赖 Biz 实现。
2. **分层调用**：Controller -> Service -> Mapper/Repository。不要从 Controller 直接访问 Mapper，也不要让 Mapper 承担业务决策。
3. **内部调用签名**：服务间 Feign 调用必须带内部签名。新增 Feign 调用时，确认现有拦截器和调用封装是否已覆盖。
4. **认证与归属**：网关负责外部鉴权；业务服务负责用户上下文、资源归属和业务权限校验。
5. **配置管理**：新增环境相关配置默认走 Nacos。本地兜底配置必须有明确原因，不要把生产秘密写入仓库。
6. **代码优先**：文档可能滞后；当文档和代码冲突时，以当前代码为准。需要修正文档时，先说明冲突点并询问用户是否修改。

## 4. 异常和响应

1. 可预期的业务错误统一使用 `com.zhoushuo.framework.common.exception.BizException`。
2. 微服务调用错误必须向上传递，不要吞掉下游错误码。
3. 跨服务调用中，`BizException` 应直接透传，不要包装成其他异常导致错误码丢失。
4. 每个模块维护自己的 `ResponseCodeEnum`，实现 `BaseExceptionInterface`。
5. 响应统一使用 `Response<T>`；成功用 `Response.success(...)`，失败用 `Response.fail(...)` 或抛 `BizException` 交给全局异常处理。
6. 框架参数校验异常可以交给全局异常处理兜底；不要为了形式统一把所有框架异常都手工包一层。

## 5. 高风险改动检查

涉及状态、事务、幂等、跨服务 DTO 或恢复链路时，必须额外检查：

1. 修改下游服务的状态、DTO 或接口契约时，必须顺着上游调用方、恢复链路、重试链路一起检查，不能只改被调用方。
2. 新增或修改状态时，检查创建、推进、重试、恢复查询、调用方、cleanup 和测试覆盖。
3. 涉及事务边界时，明确哪些操作必须本地原子提交，哪些外部 RPC 不能放进本地事务假设里。
4. 数据库状态推进优先使用 expected status 或等价条件更新，避免并发下旧状态覆盖新状态。

## 6. 测试和验证

1. 修 bug 或改行为时，优先补能失败的回归测试，再改实现。
2. 风险较低的单文件修改至少跑相关单测；跨模块、跨服务 DTO、状态机、事务改动要跑相关模块测试。
3. 结束前说明实际跑过的命令和结果。不要把“应该能过”当成验证。
4. 测试日志里如果有用例故意打印异常栈，需要说明 Maven 退出码是否为 0。
5. 无法运行测试时，说明原因和剩余风险。

## 7. 代码审查和方案评估

1. 代码审查前，先读：
   - `docs/review/审查指南.md`
   - `docs/review/已知上下文.md`
2. 审查以 bug、并发风险、事务边界、状态机漏洞、缺失测试为优先，不做风格化挑刺。
3. 本项目很多设计点已经过权衡。提出方案或质疑前，先读相关代码、文档和已知上下文；仍不确定时先问作者。
4. 收到审查意见后先验证再改。外部审查意见不盲从；正确的就修，错误的要给出技术理由。
5. 用户要求“开子代理审查”时，默认等待子代理结果并整理审查意见；除非用户明确要求“修改 / 修复 / 按意见处理”，不要自行开始改代码。
6. 审查后的额外修正默认单独提交，展示修改过程；除非作者明确要求合并提交。

## 8. Git 约定

### 8.1 工作区安全

1. 开始修改前先看当前分支和工作区状态：

```bash
git branch --show-current
git status --short
```

2. 工作区可能已有用户或其他工具留下的改动。不要回滚、覆盖、格式化无关文件。
3. 只 stage 本任务相关文件。优先使用路径白名单 `git add -- <path...>`，不要为了省事使用 `git add .`。
4. 提交前必须检查暂存区：

```bash
git diff --cached --name-only
git status --short
```

5. 如果发现无关文件已在暂存区，先撤出：

```bash
git restore --staged -- <path...>
```

### 8.2 提交格式

提交信息使用：

```text
type(scope): 中文描述
```

常用类型：

- `feat`：新增功能。
- `fix`：修复缺陷。
- `docs`：文档修改。
- `test`：测试相关。
- `refactor`：不改变行为的重构。
- `chore`：构建、脚本、配置等杂项。

示例：

```text
fix(question): 强化导入finish状态边界
docs(agents): 整理协作约定
test(excel): 补充导入恢复回归测试
```

### 8.3 提交边界

1. 一个 commit 尽量只表达一个清晰意图。
2. 代码改动和后续审查修正可以分成多个 commit，方便看修改过程。
3. 不要添加 `Co-Authored-By`。
4. 禁止擅自 `commit --amend`、rebase、reset 或改写已有提交。只有作者明确要求“合并提交 / 压成一个提交 / amend”时才允许。
5. 提交后再次查看 `git status --short`，确认剩余未提交内容都是无关文件或用户已知文件。

## 9. 文档入口

- `docs/excel-import/`：Excel 导入核心文档。
- `docs/question-chain/`：题目处理、AI、outbox/inbox 链路。
- `docs/auth-gateway/`：认证和网关。
- `docs/review/`：审查指南、已知上下文和审查日志。
- `docs/archive/`：过时文档归档。
- `sql/`：数据库迁移脚本。
