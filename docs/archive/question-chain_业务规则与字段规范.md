# 题目链路业务规则与字段规范（推测版）

## 1. 目标与范围

- 目标：明确“现阶段仅支持问答题”场景下，题目链路的字段规范与业务规则。
- 范围：Excel 文件 -> excel-parser -> question-bank 入库 -> MQ 发送 -> MQ 回传。
- 说明：本文基于当前代码推断，属于“现状契约 + 建议统一口径”文档。

## 2. 业务对象定义（当前阶段）

- 题型：仅支持问答题。
- 题目核心字段（业务语义）：
  - `content`：题目正文。
  - `answer`：答案文本。
  - `analysis`：解析文本（可空，当前 AI 回传链路未覆盖该字段）。

## 3. 链路总览

1. 上传 Excel：`excel-parser` 校验模板与内容，校验通过后保存文件元数据。
2. 解析 Excel：按 `fileId` 下载并解析，映射为 `question-bank` 批量导入 DTO。
3. 落题库：`question-bank` 为每题生成 ID，入库状态设为 `WAITING`。
4. 发送 MQ：题目进入 AI 处理队列，状态更新为 `PROCESSING`。
5. 回传结果：消费 AI 结果并批量更新题目状态与答案。

## 4. 字段规范（按链路阶段）

## 4.1 Excel 文件字段规范（输入契约）

| 列序 | Excel 列名（当前有效口径） | 必填 | 类型 | 规则 |
|---|---|---|---|---|
| 0 | `题目` | 是 | string | 不能为空；不能包含空格字符 |
| 1 | `答案` | 否 | string | 非空时不能包含空格字符 |
| 2 | `解析` | 否 | string | 非空时不能包含空格字符；且非空时 `答案` 必须非空 |

补充规则：
- 标题必须严格为：`题目、答案、解析`。
- 不允许出现有值的额外列（第 4 列及以后）。
- 空行会被跳过，但若整表无数据行会报错。
- 文件限制：仅 `xlsx`，最大 `10MB`，魔术头 `504B0304`。

## 4.2 excel-parser 内部解析字段（QuestionDataDTO）

| 字段 | 类型 | 含义 | 来源 |
|---|---|---|---|
| `questionContent` | string | 题目正文 | Excel 第 0 列 |
| `answer` | string | 答案 | Excel 第 1 列 |
| `explanation` | string | 解析 | Excel 第 2 列 |

说明：
- 代码注解为 `题目内容/答案/解析`，但上传校验器要求标题为 `题目/答案/解析`，存在命名口径冲突；当前建议以校验器口径为准。

## 4.3 excel-parser -> question-bank（批量导入契约）

请求体：`BatchImportQuestionRequestDTO`

```json
{
  "questions": [
    {
      "content": "string",
      "answer": "string",
      "analysis": "string"
    }
  ]
}
```

字段映射：
- `questionContent -> content`
- `answer -> answer`
- `explanation -> analysis`

响应体：`BatchImportQuestionResponseDTO`

```json
{
  "success": true,
  "totalCount": 10,
  "successCount": 10,
  "failedCount": 0,
  "errorMessage": "",
  "errorType": ""
}
```

## 4.4 question-bank 落库字段（t_question_bank）

| 字段 | 类型 | 来源 | 规则 |
|---|---|---|---|
| `id` | bigint | 分布式 ID | 必填，唯一 |
| `process_status` | varchar | 服务端赋值 | 新导入默认 `WAITING` |
| `created_by` | bigint | 登录上下文 | 必填 |
| `created_time` | datetime | 服务端 | 必填 |
| `updated_time` | datetime | 服务端 | 必填 |
| `content` | text | 导入请求 | 必填（业务上） |
| `answer` | text | 导入请求/AI 回写 | 可空 |
| `analysis` | text | 导入请求 | 可空 |

## 4.5 question-bank -> MQ（发给 AI）字段规范

- Topic：`TestTopic`
- 消息体：`QuestionMessage`

```json
{
  "question_id": "1234567890",
  "question_text": "题目正文"
}
```

字段说明：
- `question_id`：字符串类型题目 ID（由 Long 转 String）。
- `question_text`：题目正文，对应 `content`。

## 4.6 MQ -> question-bank（AI 回传）字段规范

- Topic：`AIProcessResultTopic`
- 消息体：`AIProcessResultMessage`

```json
{
  "question_id": "1234567890",
  "success_flag": 1,
  "error_message": "",
  "answer": "AI生成答案"
}
```

字段规则：
- `question_id`：必填，题目标识。
- `success_flag`：`1` 表示成功，其他值按失败处理。
- `error_message`：失败时建议必填。
- `answer`：成功时建议必填。

当前不支持字段：
- `analysis`（AI 回传解析）当前消息契约未定义。

## 5. 状态机规则（推测）

## 5.1 题目状态（question-bank）

- 初始入库：`WAITING`
- 送审 AI 前：批量改为 `PROCESSING`
- AI 成功后：`REVIEW_PENDING`（代码中使用）
- AI 失败后：`PROCESS_FAILED`（代码中使用）

备注：
- `REVIEW_PENDING/PROCESS_FAILED` 为实现中使用的状态值，不在公共 `ProcessStatusEnum` 中。

## 5.2 文件状态（excel-parser）

- 代码注释定义：`UPLOADED/PARSING/PARSED/FAILED`
- 当前实现实际写入：上传成功后为 `UPLOADED`，解析阶段状态流转未完整落库。

## 6. 字段映射总表（推荐作为统一口径）

| 业务语义 | Excel | 解析 DTO | 导入 DTO | 题库表 | MQ 出站 | MQ 入站 |
|---|---|---|---|---|---|---|
| 题目 ID | - | - | - | `id` | `question_id` | `question_id` |
| 题目正文 | `题目` | `questionContent` | `content` | `content` | `question_text` | - |
| 答案 | `答案` | `answer` | `answer` | `answer` | - | `answer` |
| 解析 | `解析` | `explanation` | `analysis` | `analysis` | - | - |
| 处理成功标志 | - | - | `success`(响应) | `process_status` | - | `success_flag` |
| 错误信息 | 校验错误文本 | - | `errorMessage`(响应) | （建议补字段） | - | `error_message` |

## 7. 建议的统一裁决（落地）

1. Excel 第一列命名统一为 `题目`（并同步 `QuestionDataDTO` 注解）。
2. 以 `content/answer/analysis` 作为服务内 canonical 字段命名。
3. MQ 回传若要支持 AI 解析，新增 `analysis` 字段并打通落库。
4. 统一状态枚举：补齐 `REVIEW_PENDING/PROCESS_FAILED` 到统一状态定义。
5. 错误信息持久化若有需求，补充题库表 `error_message` 字段并同步 DO/Mapper。

## 8. 关键代码依据

- Excel 校验规则：`eaqb-excel-parser/.../ExcelTemplateValidator.java`
- Excel 解析映射：`eaqb-excel-parser/.../ExcelFileServiceImpl.java`
- 导入请求 DTO：`eaqb-question-bank-api/.../BatchImportQuestionRequestDTO.java`、`QuestionDTO.java`
- 题库落库结构：`eaqb-question-bank-biz/.../QuestionDO.java`、`QuestionDOMapper.xml`
- MQ 出站/入站模型：`QuestionMessage.java`、`AIProcessResultMessage.java`
- MQ 主题：`MQConstants.java`

