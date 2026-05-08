# Excel导入链路冻结契约

## 1. 目的

本文件用于冻结 `excel-parser -> question-bank` 链路的外部契约和核心设计原则。

本次后续重构只允许整理服务内部实现，不允许随意推翻既有骨架。

## 2. 明确保留的核心设计

### 2.1 两阶段模型必须保留

必须保留：

1. 先上传并校验
2. 再按 `fileId` 触发解析

明确禁止：

- 上传即解析
- 上传后自动导入题库
- 把上传和解析强行合并成一个接口

### 2.2 上传阶段“极致校验”必须保留

上传阶段必须继续保留以下校验能力：

- 文件大小校验
- 扩展名校验
- 魔术头校验
- Excel 模板校验
- 内容级校验

原因：

- 这是整个链路中最重要的前置防线
- 避免脏文件进入后续流程
- 减少解析阶段和题库导入阶段的无效开销

### 2.3 错误明细查询能力必须保留

必须继续保留：

- 校验失败时生成 `preUploadId`
- 通过 `validation-errors` 查询详细错误

这不是附属功能，而是核心设计之一。

保留原因：

- 来自真实痛点：导入失败如果只返回“失败”，用户无法定位问题
- 错误信息必须可定位、可修改、可重试
- 这是从实习中得到的重要经验：错误反馈本身就是系统可用性的一部分

### 2.4 成功和失败的语义分离必须保留

上传阶段要继续保留两类结果语义：

- 校验失败：
  - 返回 `preUploadId`
  - 不生成 `fileId`
  - 不写正式文件记录
- 校验成功：
  - 上传到 OSS
  - 写正式文件记录
  - 返回 `fileId`

## 3. 冻结的对外契约

### 3.1 excel-parser 对外接口

以下接口路径保持不变：

- `POST /upload`
- `GET /validation-errors`
- `POST /parse-by-id`

对应代码：

- [ExcelFileController.java](D:\IntelliJ IDEA 2024.1.4\projects\Gradle\Excel AI Q-Bank\eaqb-excel-parser\eaqb-excel-parser.biz\src\main\java\com\zhoushuo\eaqb\excel\parser\biz\controller\ExcelFileController.java)

### 3.2 上传请求 DTO

以下请求结构保持不变：

- `ExcelFileUploadDTO.file`

对应代码：

- [ExcelFileUploadDTO.java](D:\IntelliJ IDEA 2024.1.4\projects\Gradle\Excel AI Q-Bank\eaqb-excel-parser\eaqb-excel-parser.biz\src\main\java\com\zhoushuo\eaqb\excel\parser\biz\model\dto\ExcelFileUploadDTO.java)

### 3.3 question-bank 批量导入契约

以下契约保持不变：

- 请求：`BatchImportQuestionRequestDTO`
- 响应：`BatchImportQuestionResponseDTO`
- Feign 调用入口：`QuestionFeign.batchImportQuestions(...)`

对应代码：

- [QuestionFeign.java](D:\IntelliJ IDEA 2024.1.4\projects\Gradle\Excel AI Q-Bank\eaqb-question-bank\eaqb-question-bank-api\src\main\java\com\zhoushuo\eaqb\question\bank\api\QuestionFeign.java)
- [BatchImportQuestionRequestDTO.java](D:\IntelliJ IDEA 2024.1.4\projects\Gradle\Excel AI Q-Bank\eaqb-question-bank\eaqb-question-bank-api\src\main\java\com\zhoushuo\eaqb\question\bank\req\BatchImportQuestionRequestDTO.java)
- [BatchImportQuestionResponseDTO.java](D:\IntelliJ IDEA 2024.1.4\projects\Gradle\Excel AI Q-Bank\eaqb-question-bank\eaqb-question-bank-api\src\main\java\com\zhoushuo\eaqb\question\bank\resp\BatchImportQuestionResponseDTO.java)

## 4. 当前重构允许修改的范围

允许修改：

- `ExcelFileServiceImpl` 内部实现
- `QuestionBankRpcService` 的错误处理与返回语义整理
- DTO -> DTO 的转换位置和方式
- 解析阶段的流程拆分
- 解析结果与导入结果的聚合逻辑
- 内部命名、注释、异常处理
- 单测补强

## 5. 当前重构禁止修改的范围

禁止修改：

- 两阶段模型
- `preUploadId / fileId` 语义
- `validation-errors` 能力
- `excel-parser -> question-bank` 的总体骨架
- `question-bank` 批量导入的 API 形状
- 网关对外可见的上传/解析入口

## 6. 这次重构的目标

本次重构不是为了“换一套设计”，而是为了：

1. 在不动骨架的前提下，让链路更清楚
2. 让上传阶段和解析阶段职责更明确
3. 让解析入库阶段的错误语义更稳定
4. 让文档、代码和测试重新一致

## 7. 一句话结论

本次重构的底线是：

- 保留“先上传并严格校验，再按 fileId 解析”的设计
- 保留“错误必须可定位”的设计
- 只整理内部实现，不推翻外部骨架
