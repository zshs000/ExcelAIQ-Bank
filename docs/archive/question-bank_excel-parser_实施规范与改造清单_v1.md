# question-bank / excel-parser 实施规范与改造清单 v1

## 1. 文档元数据

- 版本：`v1.0`
- 状态：`生效（待实施）`
- 生效日期：`2026-02-26`
- 适用范围：`eaqb-question-bank`、`eaqb-excel-parser`、相关 Feign/MQ/网关契约
- 责任人：`待指定`

## 2. 单一裁决（统一口径）

1. `Response` 外层 `success` 为唯一成功语义。  
   业务失败必须返回 `Response.fail(...)`，禁止“外层 success=true + 内层 success=false”双轨语义。

2. `batch-import` 仅允许 `POST`，生产者和消费者签名必须强一致。  
   `Controller` 与 `Feign` 均使用 `Response<BatchImportQuestionResponseDTO>`。

3. Excel 模板第一列标准名统一为：`题目`。  
   `ExcelTemplateValidator` 与 `QuestionDataDTO` 必须一致。

4. 题目 canonical 字段固定为：`content / answer / analysis`。  
   映射规则不得在各服务自行重定义。

5. MQ 契约版本化。  
   当前版本字段为：`question_id / success_flag / error_message / answer`；  
   若启用 AI 解析，升级为 `v2` 并新增 `analysis` 字段。

6. 服务层字段与 DO/Mapper/XML 必须一一对应。  
   禁止 Service 调用不存在的 mapper 方法，或写入不存在的 DO 字段。

7. AI 回写更新必须更新真实业务字段。  
   更新状态时必须同时保证 `answer/analysis`（若有）可落库。

8. `parse-by-id` 必须做资源归属校验。  
   `fileInfo.userId == LoginUserContextHolder.getUserId()` 才允许解析。

9. 网关与服务路径只允许一层前缀所有权。  
   禁止网关前缀与 Controller 前缀重复叠加。

10. 参数校验与绑定方式统一。  
   JSON：`@Validated @RequestBody`；  
   multipart：`@Validated @ModelAttribute` 或 `@RequestPart`（需显式）。

## 3. 改造任务（按优先级）

## P0（先修，阻塞构建/主流程）

1. 修复 `question-bank` 编译阻塞。
- 文件：`eaqb-question-bank-biz/.../QuestionService.java`
- 动作：补 `Map` 导入。

2. 修复 Service 与 Mapper/DO 断裂。
- 文件：`QuestionServiceImpl`、`QuestionDOMapper`、`QuestionDOMapper.xml`、`QuestionDO`
- 动作（二选一）：
  - A：补齐 `updateBatch` + `errorMessage` 字段及 SQL；
  - B：删除对应调用，统一改为存在的方法（推荐 `updateByPrimaryKeySelective` 批量循环或新增可用批量 SQL）。

3. 修复 AI 回写“状态变更但答案不落库”问题。
- 文件：`QuestionServiceImpl`、`QuestionDOMapper.xml`
- 动作：保证更新语句覆盖 `answer/analysis/process_status/updated_time`。

4. 修复分页查询条件失效。
- 文件：`QuestionServiceImpl`
- 动作：`BeanUtils.copyProperties(request, questionDO)`。

## P1（高优先，契约与安全）

1. 统一 batch-import 契约语义。
- 文件：`QuestionBankControllr`、`QuestionServiceImpl`、`QuestionBankRpcService`
- 动作：业务失败统一 `Response.fail(...)`；调用方仅依据外层 success 判定。

2. 统一 batch-import HTTP 契约。
- 文件：`QuestionBankControllr`
- 动作：`@RequestMapping("/batch-import")` -> `@PostMapping("/batch-import")`，返回泛型与 Feign 一致。

3. 增加 `parse-by-id` 权限校验。
- 文件：`ExcelFileServiceImpl`
- 动作：查询 `FileInfoDO` 后校验归属，不通过返回 `NO_PERMISSION`。

4. 路由前缀去重。
- 文件：`eaqb-gateway/src/main/resources/application.yml`、`ExcelFileController`
- 动作：网关/服务保留一侧前缀所有权。

5. 完整消费导入返回字段。
- 文件：`ExcelFileServiceImpl`、`ExcelProcessVO`
- 动作：补 `failedCount -> failCount` 映射。

## P2（质量提升）

1. 补齐 DTO 校验注解。
- 文件：`BatchImportQuestionRequestDTO`、`QuestionDTO`、`CreateQuestionDTO`、`UpdateQuestionDTO`、`QuestionPageQueryDTO`
- 动作：`@NotEmpty/@NotBlank/@Min/@Max/@Valid` 等。

2. 规范命名与拼写。
- 文件：`QuestionBankControllr`、`uploadAExcel`、`ResponseCodeEnum(EXCEl-10001)`
- 动作：统一拼写和方法命名。

3. 补齐 `eaqb-excel-parser-api` 契约层。
- 文件：`eaqb-excel-parser-api`
- 动作：增加 API 常量、DTO、Feign（对外可复用）。

## 4. 验收清单（完成定义）

## 构建验收

1. `mvn -q -pl eaqb-question-bank/eaqb-question-bank-biz,eaqb-excel-parser/eaqb-excel-parser.biz -am -DskipTests compile` 通过。

## 接口契约验收

1. `batch-import` 业务失败时外层 `success=false`。
2. `batch-import` 仅接受 `POST`。
3. `excel-parser` 上传路径在网关下无双前缀歧义。

## 安全验收

1. 非资源所有者调用 `parse-by-id` 返回无权限。

## 数据一致性验收

1. AI 回写后，`process_status` 与 `answer` 同步更新。
2. 分页查询按条件过滤生效。

## 字段规范验收

1. Excel 模板第一列名与解析注解一致（均为 `题目`）。
2. 导入结果中的 `failedCount` 在 `excel-parser` 响应中可见。

## 5. 变更记录（模板）

| 日期 | 版本 | 变更人 | 变更内容 |
|---|---|---|---|
| 2026-02-26 | v1.0 | 待填写 | 首版实施规范与改造清单 |

