# ExcelParserUtil 异常码折叠问题说明

## 1. 问题概述

在 `eaqb-excel-parser` 的解析链路中，分块导入阶段本应透传下游业务异常码（例如题库服务返回的业务错误），但实际会被折叠为本服务的通用系统错误码 `EXCEL-10000`。

这会导致调用方无法区分真实失败原因，影响重试、告警和用户提示。

## 2. 触发链路

1. `ExcelParseAppService.parseExcelFileById` 调用 `ExcelParserUtil.parseExcelInChunks(...)`。  
2. `parseExcelInChunks` 内部通过 `QuestionExcelListener.flush()` 执行 `chunkConsumer.accept(...)`。  
3. 该 `chunkConsumer` 在 `ExcelParseAppService` 中会调用 `questionBankRpcService.appendImportChunk(...)`。  
4. 若下游抛出 `BizException`（含业务 errorCode），在 `ExcelParserUtil` 被 `catch (Exception)` 捕获并包装为 `RuntimeException`。  
5. 上层 `parseExcelFileById` 无法再命中 `catch (BizException)`，转而命中通用 `catch (Exception)`，最终改写为 `SYSTEM_ERROR(EXCEL-10000)`。

## 3. 根因分析

这是典型的“架构演进后异常边界未同步”问题：

- 早期 `ExcelParserUtil` 作为纯解析工具，统一包装异常为运行时异常影响较小。  
- 后续在其回调执行路径中引入了 RPC/业务异常语义（`BizException` + errorCode）。  
- 工具层仍保留“统一包装”策略，导致业务异常语义丢失。

## 4. 影响范围

- **错误码契约失真**：调用方拿不到真实业务码。  
- **重试策略失真**：可重试/不可重试边界变模糊。  
- **观测能力下降**：日志和监控面板聚合为系统错误，排障效率下降。  
- **测试不稳定**：依赖业务码断言的测试会出现预期不一致。

## 5. 建议修复

建议在 `ExcelParserUtil.parseExcelInChunks` 中做异常分层处理：

1. `BizException` 直接透传，不要包装。  
2. 仅对未知异常（非 `BizException`）做统一包装。  
3. 保留原始 cause，避免日志与错误码脱钩。

示意策略：

- `catch (BizException e) { throw e; }`
- `catch (Exception e) { throw new RuntimeException(..., e); }`

## 6. 结论

该问题不是单点代码失误，而是系统从“本地工具调用”演进到“跨服务契约调用”后，异常处理策略未升级造成的语义折叠。  
本质属于“契约稳定性问题”，应优先修复。
