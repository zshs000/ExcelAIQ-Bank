# Excel 导入 contentHash 风险与解决方案

## 1. 背景

导入链路采用分块幂等：

- `excel-parser` 计算并上送 `contentHash`。
- `question-bank` 按 `(batchId, chunkNo, rowCount, contentHash)` 判断是否重复分块。

历史实现中，`contentHash` 的输入是“字段值 + `\n` 分隔”。

## 2. 风险说明

风险不在 `SHA-256` 本身，而在“哈希前输入串行化存在边界歧义”。

当字段值包含换行时，不同字段边界可能拼成相同字节流，导致：

- 不同内容产生同一 `contentHash`。
- 下游误判 `duplicate`，造成静默数据错误。

## 3. 解决方案（已落地）

### 3.1 协议升级

- `AppendImportChunkRequestDTO` 新增 `hashVersion` 字段。
- 当前版本固定为 `v2`。

### 3.2 哈希算法升级为 v2

引入统一工具 `ImportChunkHashUtil`，采用 v2 编码规则：

- 字段顺序固定：`content -> answer -> analysis`。
- 每个字段编码为：`4字节长度前缀 + UTF-8字段字节`。
- 再对整个输入流执行 `SHA-256`。

该规则消除了字段边界歧义。

### 3.3 双端统一实现

- 上游 (`excel-parser`) 使用同一工具计算并上送：`hashVersion=v2 + contentHash`。
- 下游 (`question-bank`) 使用同一工具基于 `rows` 重算并校验。

### 3.4 下游校验策略

在 `appendImportChunk` 阶段执行重算校验：

- 一致：继续后续幂等判断。
- 不一致：标记批次失败并抛 `QUESTION_IMPORT_CHUNK_CONFLICT`。

## 4. 当前实现位置

- 协议字段：`AppendImportChunkRequestDTO.hashVersion`
- 统一算法：`ImportChunkHashUtil`
- 上游赋值：`ExcelParseAppService.buildAppendChunkRequest`
- 下游校验：`QuestionImportBatchAppService.ensureChunkHashMatchesPayload`

## 5. 验证情况

- `excel-parser` 侧相关测试通过。
- `question-bank` 的 `QuestionImportBatchAppServiceTest` 通过。
- 新增打印测试用于直观看到：
  - v1 拼接文本会把两组不同字段边界拼成同一串。
  - v2 因长度前缀不同，输入流和哈希都不同。

## 6. 收益

- 解决“不同内容同 hash”的核心风险。
- 将单边信任升级为双边一致性校验。
- 为后续协议演进（`hashVersion`）预留扩展位。
