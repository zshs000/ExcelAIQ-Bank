# question-bank 与 excel-parser 契约规范问题文档

## 1. 范围与依据

- 服务范围：`eaqb-question-bank`、`eaqb-excel-parser`（含其上下游契约接口与消息契约）。
- 依据材料：
  - 现有问题文档：`question-bank_excel-parser_问题审查与修改建议.md`
  - 项目知识文档：`项目知识点文档.md`
  - 代码实现：Controller/Feign/DTO/MQ/Mapper。

## 2. 契约问题清单（重点：生产者-消费者不一致）

### P0-1 批量导入返回语义双轨，消费者极易误判

- 生产者（question-bank）：
  - `BatchImportQuestionResponseDTO.success=false` 后仍 `Response.success(response)` 返回（`QuestionServiceImpl.java:124,131,193,201`）。
- 消费者（excel-parser）：
  - 仅先校验外层 `Response.isSuccess()`（`QuestionBankRpcService.java:18`），再在上层校验内层 `data.success`（`ExcelFileServiceImpl.java:279`）。
- 偏差说明：
  - 外层 `success` 与内层 `success` 语义重叠，且失败分支外层仍为成功。
- 影响：
  - 新调用方很容易只看外层成功而误判业务成功。
- 规范建议：
  - 统一“单一成功语义”：业务失败直接 `Response.fail(...)`；或明确规定只看内层字段并强制所有调用方按此实现。

### P0-2 Excel 模板字段口径不一致（题目 vs 题目内容）

- 生产者（模板校验规则）：
  - 校验标题固定为 `题目、答案、解析`（`ExcelTemplateValidator.java:187`）。
- 消费者（解析 DTO）：
  - 首列注解为 `@ExcelProperty(value = "题目内容", index = 0)`（`QuestionDataDTO.java:8`）。
- 文档佐证：
  - `项目知识点文档.md:167` 已指出该差异。
- 偏差说明：
  - 同一列在不同契约点出现两种名称。
- 影响：
  - 模板说明、前端导出模板、后端解析规则容易漂移。
- 规范建议：
  - 统一第一列标准名（推荐固定为一个名称），并在校验器与 DTO 注解中保持一致。

### P0-3 MQ 结果字段与处理能力不一致（文档说可生成解析，契约只收答案）

- 文档预期：
  - AI 处理“生成解析或校验答案”（`README.md:121`）。
- 实际契约（question-bank 消费）：
  - 结果消息字段仅 `question_id/success_flag/error_message/answer`（`AIProcessResultMessage.java:15,18,21,24`）。
  - 批处理只使用 `answer`（`BatchProcessorService.java:111,113`）。
- 偏差说明：
  - 文档能力描述包含“解析”，但消息与消费逻辑无对应字段。
- 影响：
  - AI 若返回解析数据，当前链路无法消费落库。
- 规范建议：
  - 明确消息 schema（含字段、类型、必填、版本）；若需解析，补 `analysis` 字段并打通落库链路。

### P1-1 批量导入 HTTP 契约约束不严格，易产生漂移

- 消费者契约（excel-parser 通过 Feign）：
  - 约定 `POST /question/batch-import`（`QuestionFeign.java:22`）。
- 生产者实现（question-bank）：
  - `@RequestMapping("/batch-import")` 未限定方法（`QuestionBankControllr.java:17`）。
  - 返回类型 `Response<?>`，未与 Feign 的泛型返回强绑定（`QuestionBankControllr.java:19` vs `QuestionFeign.java:23`）。
- 偏差说明：
  - 编译期无法强约束“方法 + 返回体结构”一致性。
- 影响：
  - 后续修改极易引入不兼容而不被及时发现。
- 规范建议：
  - 生产者改为 `@PostMapping` 且签名统一为 `Response<BatchImportQuestionResponseDTO>`。

### P1-2 网关路径契约与服务路径重复叠加

- 网关：
  - 路由 `/excel-parser/**` 且 `StripPrefix=1`（`eaqb-gateway/src/main/resources/application.yml:28,30`）。
- 服务实现（excel-parser）：
  - 控制器再声明 `@RequestMapping("/excel-parser")`（`ExcelFileController.java:15`）。
- 偏差说明：
  - 路由层和服务层同时维护同一前缀。
- 影响：
  - 外部真实路径与调用方认知容易不一致（常见双前缀/404）。
- 规范建议：
  - 只保留一层前缀所有权：要么网关维护，要么服务维护。

### P1-3 导入结果字段消费不完整（failedCount 未对齐）

- 生产者返回字段：
  - `failedCount/errorType` 等（`BatchImportQuestionResponseDTO.java:23,27`）。
- 消费者映射：
  - 仅映射 `successCount/errorMessage`（`ExcelFileServiceImpl.java:283,286`）。
  - 但自身 VO 已定义 `failCount`（`ExcelProcessVO.java:14`）。
- 偏差说明：
  - 生产者字段存在，消费者未消费，造成结果口径不完整。
- 影响：
  - 前端无法稳定拿到失败数量，展示与统计不一致。
- 规范建议：
  - 明确“导入结果最小必返字段集”，并完成 `failedCount -> failCount` 映射。

### P1-4 AI 结果落库契约与 SQL 不一致（字段被写丢）

- 服务层：
  - 更新 AI 结果时写入 `answer` 并调用 `updateByPrimaryKey`（`QuestionServiceImpl.java:420,424`）。
- 持久层 SQL：
  - `updateByPrimaryKey` 仅更新 `process_status/created_time/updated_time/created_by`（`QuestionDOMapper.xml:209-212`）。
- 偏差说明：
  - 上层认为“已写答案”，底层 SQL 实际未更新答案/解析。
- 影响：
  - AI 处理完成后数据状态与内容不一致。
- 规范建议：
  - 统一更新契约：改用 `updateByPrimaryKeySelective` 或补全 SQL 字段。

### P2-1 服务内部实现契约断裂（Model/Mapper 与 Service 字段不一致）

- Service 使用：
  - 调用 `updateBatch(...)`（`QuestionServiceImpl.java:472,531`）。
  - 设置 `setErrorMessage(...)`（`QuestionServiceImpl.java:518`）。
- 实际模型/Mapper：
  - `QuestionDO` 无 `errorMessage` 字段（`QuestionDO.java`）。
  - `QuestionDOMapper` 无 `updateBatch` 方法（`QuestionDOMapper.java`）。
- 偏差说明：
  - Service 对下层契约的假设与真实定义不一致。
- 影响：
  - 直接导致构建失败或功能不可用。
- 规范建议：
  - 二选一：补齐下层契约（字段+方法+SQL）或删除上层假设逻辑。

### P2-2 excel-parser API 模块为空壳，外部契约无法复用

- 当前现状：
  - `eaqb-excel-parser-api` 仅有 `pom.xml`，无对外 DTO/Feign/常量定义。
- 对照：
  - `eaqb-question-bank-api` 已承载对外 Feign 与 DTO 契约。
- 偏差说明：
  - excel-parser 的对外契约目前散落在 biz Controller 中，缺少可复用/可编译校验的“契约载体”。
- 影响：
  - 其他服务或网关侧难以通过依赖 API 模块复用一致契约，改动时回归成本高。
- 规范建议：
  - 在 `eaqb-excel-parser-api` 中补齐 API 常量、请求/响应 DTO、Feign 接口，biz 仅负责实现。

## 3. 与现有文档的对齐结论

- 与 `question-bank_excel-parser_问题审查与修改建议.md:97` 一致：批量导入存在“外层成功、内层失败”的双轨语义问题。
- 与 `项目知识点文档.md:167` 一致：模板标题与解析 DTO 列名存在口径差异。
- 新增补充：本次将问题整理为“契约项”并明确到生产者/消费者双方代码点，便于后续按契约治理闭环。

## 4. 建议的统一契约基线（落地版）

1. REST 契约：单一成功语义、固定 HTTP 方法、固定返回泛型。
2. Excel 模板契约：列名、列序、必填/可空规则写成单一规范源（代码与文档同源）。
3. MQ 契约：定义可版本化 schema（字段名、类型、可选性、兼容策略）。
4. 持久化契约：Service 字段与 DO/Mapper/XML 三层字段必须一一对应。
5. 验证机制：在 CI 中增加“契约一致性检查”（最少包含 Feign-Controller、DTO-Mapper、MQ Schema 校验）。
