# Excel AI Q-Bank 审查报告（重点：question-bank、excel-parser）

## 1. 审查范围与方法

- 范围：
  - 基础/对照服务：`eaqb-user`、`eaqb-auth`、`eaqb-gateway`、`eaqb-oss`、`eaqb-framework`
  - 重点服务：`eaqb-question-bank`、`eaqb-excel-parser`
- 方法：
  - 代码静态审查（Controller/DTO/Service/Mapper/Feign/配置）
  - 调用链梳理（Gateway 路由 + Feign + MQ）
  - 最小编译验证（`mvn -q -pl eaqb-question-bank/eaqb-question-bank-biz,eaqb-excel-parser/eaqb-excel-parser.biz -am -DskipTests compile`）

## 2. 项目总体架构（结合代码实际）

- 网关层：`eaqb-gateway` 统一入口，基于 Spring Cloud Gateway + Sa-Token，负责路由、鉴权、透传 `userId` 请求头。
- 业务服务层：
  - `eaqb-auth`：登录注册、验证码、密码修改。
  - `eaqb-user`：用户信息、注册、密码更新。
  - `eaqb-oss`：文件上传与短链生成。
  - `eaqb-excel-parser`：Excel 校验、上传 OSS、解析并调用题库批量导入。
  - `eaqb-question-bank`：题目 CRUD、批量导入、MQ 发送与 AI 结果消费。
  - `eaqb-distributed-id-generator`：分布式 ID。
- 基础设施：
  - `eaqb-framework` 提供统一 `Response`、`BizException`、上下文透传（Filter + Feign 拦截器）、通用组件。
  - Nacos/Redis/MySQL/RocketMQ（从配置和依赖可确认）。

### 2.1 关键调用链（重点链路）

- 用户上传链路：
  - `Gateway -> excel-parser -> oss`（上传文件）
  - `excel-parser -> question-bank`（批量导入题目）
- AI 异步链路：
  - `question-bank` 发送题目到 MQ
  - AI 服务回写 MQ
  - `question-bank` 消费结果并写 Redis/批处理更新 DB（设计上）

## 3. “相对完善”服务里的可复用规范基线

从 `auth/user/framework` 可以提炼的现有基线：

- 入参对象显式校验：`@Validated @RequestBody` + DTO 上 `@NotBlank/@PhoneNumber`。
- 统一返回结构：`Response<T>`。
- 统一异常兜底：`GlobalExceptionHandler` 处理 `BizException` + `MethodArgumentNotValidException`。
- 统一错误码语义：`模块前缀-编号`（如 `USER-10001`）。

结论：项目本身已有“统一参数规范”的雏形，但在 `question-bank` 和 `excel-parser` 中出现明显偏离。

## 4. 重点问题清单（按严重级别）

## P0（必须先修，当前会阻塞构建或主流程）

1. `question-bank` 当前无法通过编译：`QuestionService` 缺少 `Map` 导入。
- 证据：
  - `eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/QuestionService.java:66`
  - `eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/QuestionService.java:73`
  - 编译报错已复现：`找不到符号 Map`
- 影响：服务无法构建发布，整条题库链路阻断。

2. `QuestionServiceImpl` 调用了未定义的方法/字段（修完上一个错误后仍会继续报错）。
- 证据：
  - 调用 `questionDOMapper.updateBatch(...)`，但 mapper 接口无该方法。
    - 调用点：`.../QuestionServiceImpl.java:472`, `.../QuestionServiceImpl.java:531`
    - mapper 方法列表：`.../QuestionDOMapper.java:9-51`（无 `updateBatch`）
  - 调用 `updateDO.setErrorMessage(...)`，但 `QuestionDO` 无 `errorMessage` 字段。
    - 调用点：`.../QuestionServiceImpl.java:518`
    - `QuestionDO` 字段：`.../QuestionDO.java:15-29`
- 影响：编译失败或逻辑落空。

## P1（高风险，运行时行为错误或安全问题）

3. 分页查询条件拷贝方向写反，导致筛选条件失效。
- 证据：`.../QuestionServiceImpl.java:270` 使用 `BeanUtils.copyProperties(questionDO, request);`
- 问题：应为 `copyProperties(request, questionDO)`。
- 影响：分页查询按条件过滤不生效，接口行为与前端预期不一致。

4. AI 结果更新使用 `updateByPrimaryKey`，但 SQL 未更新 `answer/analysis`，导致结果不落库。
- 证据：
  - 调用点：`.../QuestionServiceImpl.java:424`, `:479`, `:538`
  - SQL：`.../mapper/QuestionDOMapper.xml:207-213` 仅更新 `process_status/created_time/updated_time/created_by`
- 影响：AI 回写“看起来成功”，但题目答案等核心字段可能未更新。

5. `excel-parser` 的 `parse-by-id` 缺少资源归属校验，存在越权风险。
- 证据：
  - 查询文件：`.../ExcelFileServiceImpl.java:233`
  - 该方法中未校验 `fileInfo.userId == LoginUserContextHolder.getUserId()`
  - 对比 `getValidationErrors` 已有权限校验：`.../ExcelFileServiceImpl.java:217-219`
- 影响：用户可通过猜测 `fileId` 触发他人文件解析。

6. 网关路由与 `excel-parser` 控制器路径叠加导致接口前缀混乱。
- 证据：
  - Gateway 路由对 `/excel-parser/**` 做 `StripPrefix=1`：`eaqb-gateway/src/main/resources/application.yml:28-30`
  - 控制器又声明 `@RequestMapping("/excel-parser")`：`.../ExcelFileController.java:15`
- 影响：外部调用路径容易变成 `/excel-parser/excel-parser/upload`，对调用方不透明，易 404。

7. 批量导入接口“业务失败”仍返回 `Response.success(...)`，外层成功语义与内层 `DTO.success=false` 双轨并存。
- 证据：
  - 失败分支仍返回 `Response.success(response)`：`.../QuestionServiceImpl.java:131`, `:201`
  - RPC 层先看外层 success：`.../QuestionBankRpcService.java:18`
- 影响：跨服务调用契约语义不清晰，后续容易出现误判和重复兜底逻辑。

## P2（中风险，一致性/可维护性问题）

8. `batch-import` 使用 `@RequestMapping` 而非显式 `@PostMapping`，HTTP 方法约束不清。
- 证据：`.../QuestionBankControllr.java:17`

9. 参数校验风格不统一，校验粒度明显弱于 `auth/user`。
- 证据：
  - `QuestionExternalController` 入参无 `@Validated/@Valid`：`.../QuestionExternalController.java:37,47,69`
  - `BatchImportQuestionRequestDTO`、`QuestionDTO` 无 `@NotEmpty/@NotBlank`：`.../BatchImportQuestionRequestDTO.java:9`, `.../QuestionDTO.java:16,21,26`
  - `UpdateQuestionDTO`、`QuestionPageQueryDTO` 无任何约束：`.../UpdateQuestionDTO.java:11`, `.../QuestionPageQueryDTO.java:8`

10. `excel-parser` 上传接口参数绑定不够显式，且异常处理未覆盖常见 multipart 绑定异常。
- 证据：
  - 上传方法仅 `@Validated ExcelFileUploadDTO`，无 `@ModelAttribute/@RequestPart`：`.../ExcelFileController.java:28`
  - 全局异常仅处理 `MethodArgumentNotValidException`：`.../excel/parser/biz/exception/GlobalExceptionHandler.java:37`
- 影响：缺参/绑定异常时错误返回可能不稳定。

11. 错误码拼写不一致。
- 证据：`EXCEl-10001`（`l` 大小写错误）`.../excel/parser/biz/enums/ResponseCodeEnum.java:13`

12. 命名与可读性问题（影响协作质量）。
- 证据：
  - 类名拼写：`QuestionBankControllr`（应为 Controller）
  - 方法命名：`uploadAExcel` 不符合常规英语命名

## 5. 修改建议（按落地优先级）

## 第一阶段（当天完成，先恢复可构建与主链路正确性）

1. 修复编译阻塞。
- `QuestionService` 增加 `import java.util.Map;`
- 在 `QuestionDOMapper` 与 XML 增加 `updateBatch(List<QuestionDO>)`，或移除调用统一改为循环 `updateByPrimaryKeySelective`。
- 若确需记录失败原因，给 `QuestionDO`/表结构增加 `error_message`，并同步 mapper；否则删除 `setErrorMessage` 路径。

2. 修复分页过滤错误。
- 改为 `BeanUtils.copyProperties(request, questionDO)`。

3. 修复 AI 回写落库。
- 批量/单条更新统一走 `updateByPrimaryKeySelective` 或新增专用 SQL（显式更新 `answer/analysis/process_status/updated_time`）。

4. 增加 `parse-by-id` 的用户归属校验。
- 与 `getValidationErrors` 同步：校验 `fileInfo.userId` 与当前用户一致，不一致返回 `NO_PERMISSION`。

## 第二阶段（1-2 天，统一接口契约和参数规范）

1. 统一 Controller 入参约定。
- JSON：`@Validated @RequestBody`
- multipart：`@Validated @ModelAttribute` 或 `@RequestPart`
- 所有外部接口显式指定 `@PostMapping/@GetMapping/...`，避免泛 `@RequestMapping`

2. 统一 DTO 校验层。
- `BatchImportQuestionRequestDTO`：`@NotEmpty` + `@Valid`
- `QuestionDTO.content`：`@NotBlank`
- `QuestionPageQueryDTO.page/pageSize`：`@Min(1)`、`@Max(...)`
- `UpdateQuestionDTO`：补充长度/空值策略（至少一个字段可更新）

3. 统一失败语义。
- 约定“业务失败必须 `Response.fail(...)`”，不要再用 `Response.success(data.success=false)` 双轨。

4. 对齐网关与服务路径。
- 二选一：
  - 保留网关 `StripPrefix=1`，则服务内不要再加 `/excel-parser` 一级前缀
  - 或取消该路由的 strip，由服务自己维护前缀

## 第三阶段（持续优化）

1. 命名与拼写治理（类名/方法名/错误码）。
2. 清理重复注释与注释掉的大段旧代码。
3. 为核心链路补最小集成测试：
- Excel 上传校验
- 批量导入失败/成功
- AI 回写状态更新
- 越权访问校验

## 6. 结论

- 架构主干是合理的：网关鉴权 + 业务解耦 + MQ 异步 + framework 共性沉淀。
- 当前主要问题不在“有没有架构”，而在“重点服务实现质量不稳定”：
  - `question-bank` 已出现构建级错误；
  - `excel-parser` 和 `question-bank` 在参数规范、契约语义、权限边界上存在明显不一致。
- 建议按“先可构建、再统一规范、后补测试”的顺序推进，优先解决 P0/P1。
