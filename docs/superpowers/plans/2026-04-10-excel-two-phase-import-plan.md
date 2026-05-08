# Excel Two-Phase Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace parse-time full-memory aggregation with a chunked temp-table import flow that keeps final question insertion atomic inside `question-bank`.

**Architecture:** `question-bank` becomes the owner of import batches, temp rows, idempotent chunk appends, and the final local-transaction commit into `t_question`. `excel-parser` stops building one big `List<QuestionDataDTO>` for formal import and instead streams rows into fixed-size chunks, appends them to the remote batch, then calls `finishImportBatch` and `commitImportBatch`.

**Tech Stack:** Spring Boot, OpenFeign, MyBatis XML mapper, EasyExcel, JUnit 5, Mockito

---

## File Map

### Question-Bank API / Contract

- Modify: `eaqb-question-bank/eaqb-question-bank-api/src/main/java/com/zhoushuo/eaqb/question/bank/api/QuestionFeign.java`
- Create: `eaqb-question-bank/eaqb-question-bank-api/src/main/java/com/zhoushuo/eaqb/question/bank/req/CreateImportBatchRequestDTO.java`
- Create: `eaqb-question-bank/eaqb-question-bank-api/src/main/java/com/zhoushuo/eaqb/question/bank/req/AppendImportChunkRequestDTO.java`
- Create: `eaqb-question-bank/eaqb-question-bank-api/src/main/java/com/zhoushuo/eaqb/question/bank/req/FinishImportBatchRequestDTO.java`
- Create: `eaqb-question-bank/eaqb-question-bank-api/src/main/java/com/zhoushuo/eaqb/question/bank/req/CommitImportBatchRequestDTO.java`
- Create: `eaqb-question-bank/eaqb-question-bank-api/src/main/java/com/zhoushuo/eaqb/question/bank/req/ImportQuestionRowDTO.java`
- Create: `eaqb-question-bank/eaqb-question-bank-api/src/main/java/com/zhoushuo/eaqb/question/bank/resp/CreateImportBatchResponseDTO.java`
- Create: `eaqb-question-bank/eaqb-question-bank-api/src/main/java/com/zhoushuo/eaqb/question/bank/resp/AppendImportChunkResponseDTO.java`
- Create: `eaqb-question-bank/eaqb-question-bank-api/src/main/java/com/zhoushuo/eaqb/question/bank/resp/FinishImportBatchResponseDTO.java`
- Create: `eaqb-question-bank/eaqb-question-bank-api/src/main/java/com/zhoushuo/eaqb/question/bank/resp/CommitImportBatchResponseDTO.java`

### Question-Bank Batch Persistence / App Service

- Create: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/dataobject/QuestionImportBatchDO.java`
- Create: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/dataobject/QuestionImportTempDO.java`
- Create: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/mapper/QuestionImportBatchDOMapper.java`
- Create: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/mapper/QuestionImportTempDOMapper.java`
- Create: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/enums/QuestionImportBatchStatusEnum.java`
- Create: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionImportBatchAppService.java`
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/controller/QuestionBankControllr.java`
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/QuestionService.java`
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionServiceImpl.java`
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/enums/ResponseCodeEnum.java`
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionServiceImplTest.java`
- Create: `docs/sql/2026-04-10-add-question-import-batch-and-temp-table.sql`

### Excel-Parser Orchestration

- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/main/java/com/zhoushuo/eaqb/excel/parser/biz/config/EasyExcelConfig.java`
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/main/java/com/zhoushuo/eaqb/excel/parser/biz/rpc/QuestionBankRpcService.java`
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/main/java/com/zhoushuo/eaqb/excel/parser/biz/service/app/ExcelParseAppService.java`
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/main/java/com/zhoushuo/eaqb/excel/parser/biz/util/ExcelParserUtil.java`
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/main/java/com/zhoushuo/eaqb/excel/parser/biz/listener/QuestionExcelListener.java`
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/test/java/com/zhoushuo/eaqb/excel/parser/biz/rpc/QuestionBankRpcServiceTest.java`
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/test/java/com/zhoushuo/eaqb/excel/parser/biz/service/app/ExcelParseAppServiceTest.java`
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/test/java/com/zhoushuo/eaqb/excel/parser/biz/util/ExcelParserUtilTest.java`

## Task 1: Add Failing Tests For Question-Bank Batch Semantics

**Files:**
- Test: `eaqb-question-bank/eaqb-question-bank-biz/src/test/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionServiceImplTest.java`

- [ ] Step 1: Add a failing test that `createImportBatch` returns a new batch in `APPENDING`.
- [ ] Step 2: Add a failing test that first `appendImportChunk` writes temp rows and updates counters.
- [ ] Step 3: Add a failing test that duplicate `appendImportChunk` with same `batchId + chunkNo + hash` is treated as idempotent success.
- [ ] Step 4: Add a failing test that duplicate `appendImportChunk` with different hash marks batch `FAILED` and throws.
- [ ] Step 5: Add a failing test that `finishImportBatch` only allows `APPENDING -> READY`.
- [ ] Step 6: Add a failing test that `commitImportBatch` only allows `READY -> COMMITTED` and inserts formal questions atomically.
- [ ] Step 7: Run `mvn -q -Dtest=QuestionServiceImplTest test` and confirm the new tests fail for missing behavior.

## Task 2: Implement Question-Bank Batch Tables, Mapper, And App Service

**Files:**
- Create: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/dataobject/QuestionImportBatchDO.java`
- Create: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/dataobject/QuestionImportTempDO.java`
- Create: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/mapper/QuestionImportBatchDOMapper.java`
- Create: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/domain/mapper/QuestionImportTempDOMapper.java`
- Create: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/enums/QuestionImportBatchStatusEnum.java`
- Create: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionImportBatchAppService.java`
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/enums/ResponseCodeEnum.java`
- Create: `docs/sql/2026-04-10-add-question-import-batch-and-temp-table.sql`

- [ ] Step 1: Add SQL for `question_import_batch` and `question_import_temp`, including unique index on `(batch_id, chunk_no)`.
- [ ] Step 2: Implement DOs and mapper interfaces plus XML mappings for insert, select, count, list-by-batch, and status transitions.
- [ ] Step 3: Implement `QuestionImportBatchStatusEnum` with `APPENDING`, `READY`, `COMMITTED`, `FAILED`, `ABORTED`.
- [ ] Step 4: Implement `QuestionImportBatchAppService#createImportBatch` using distributed ID generation and current user context.
- [ ] Step 5: Implement `appendImportChunk` with stored `rowCount` and `contentHash`, first-write-wins idempotency, and drift detection.
- [ ] Step 6: Implement `finishImportBatch` to validate expected counts and transit to `READY`.
- [ ] Step 7: Implement `commitImportBatch` in one local transaction: load temp rows, build `QuestionDO`s, batch insert formal questions, then transit batch to `COMMITTED`.
- [ ] Step 8: Re-run `mvn -q -Dtest=QuestionServiceImplTest test` and get green.

## Task 3: Expose New Question-Bank Batch APIs

**Files:**
- Modify: `eaqb-question-bank/eaqb-question-bank-api/src/main/java/com/zhoushuo/eaqb/question/bank/api/QuestionFeign.java`
- Create: request / response DTOs under `eaqb-question-bank-api`
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/controller/QuestionBankControllr.java`
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/QuestionService.java`
- Modify: `eaqb-question-bank/eaqb-question-bank-biz/src/main/java/com/zhoushuo/eaqb/question/bank/biz/service/impl/QuestionServiceImpl.java`

- [ ] Step 1: Add DTO tests indirectly by compiling `question-bank-api` after type definitions are added.
- [ ] Step 2: Add `createImportBatch`, `appendImportChunk`, `finishImportBatch`, `commitImportBatch` to `QuestionFeign`.
- [ ] Step 3: Add controller endpoints that delegate straight to `QuestionService`.
- [ ] Step 4: Keep old `batchImportQuestions` intact for now unless implementation becomes dead code after parser cutover.
- [ ] Step 5: Run `mvn -q -DskipTests compile` for `question-bank` if API-only coverage is insufficient.

## Task 4: Add Failing Tests For Excel-Parser Chunked Orchestration

**Files:**
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/test/java/com/zhoushuo/eaqb/excel/parser/biz/rpc/QuestionBankRpcServiceTest.java`
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/test/java/com/zhoushuo/eaqb/excel/parser/biz/service/app/ExcelParseAppServiceTest.java`
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/test/java/com/zhoushuo/eaqb/excel/parser/biz/util/ExcelParserUtilTest.java`

- [ ] Step 1: Add a failing RPC test for each new `question-bank` call and its null/fail DTO handling.
- [ ] Step 2: Add a failing parse-app test that two parsed chunks trigger `create -> append(1) -> append(2) -> finish -> commit`.
- [ ] Step 3: Add a failing parse-app test that same-content chunk retry path is treated as success if RPC replays safely.
- [ ] Step 4: Add a failing parse-app test that append failure marks file status `FAILED` and aborts commit.
- [ ] Step 5: Add a failing parser-util test that rows are streamed to a consumer in fixed-size chunks without aggregating the whole file.
- [ ] Step 6: Run `mvn -q -Dtest=ExcelParseAppServiceTest,QuestionBankRpcServiceTest,ExcelParserUtilTest test` and confirm failures.

## Task 5: Implement Excel Streaming Chunk Push

**Files:**
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/main/java/com/zhoushuo/eaqb/excel/parser/biz/config/EasyExcelConfig.java`
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/main/java/com/zhoushuo/eaqb/excel/parser/biz/rpc/QuestionBankRpcService.java`
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/main/java/com/zhoushuo/eaqb/excel/parser/biz/service/app/ExcelParseAppService.java`
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/main/java/com/zhoushuo/eaqb/excel/parser/biz/util/ExcelParserUtil.java`
- Modify: `eaqb-excel-parser/eaqb-excel-parser.biz/src/main/java/com/zhoushuo/eaqb/excel/parser/biz/listener/QuestionExcelListener.java`

- [ ] Step 1: Add configurable parse chunk size to `EasyExcelConfig`.
- [ ] Step 2: Extend `QuestionBankRpcService` with typed wrappers for the four new APIs and preserve downstream error semantics.
- [ ] Step 3: Refactor `ExcelParserUtil` and `QuestionExcelListener` to push `List<QuestionDataDTO>` chunks to a callback instead of returning one full list.
- [ ] Step 4: Change `ExcelParseAppService` to orchestrate `createImportBatch -> appendImportChunk* -> finishImportBatch -> commitImportBatch`.
- [ ] Step 5: Keep file status handling consistent: only mark parsed after commit succeeds; any append/finish/commit failure marks file `FAILED`.
- [ ] Step 6: Re-run `mvn -q -Dtest=ExcelParseAppServiceTest,QuestionBankRpcServiceTest,ExcelParserUtilTest test` and get green.

## Task 6: Final Verification

**Files:**
- Modify if required: any broken tests or mapper XML discovered during integration

- [ ] Step 1: Run `mvn -q -Dtest=QuestionServiceImplTest test` in `eaqb-question-bank`.
- [ ] Step 2: Run `mvn -q -Dtest=ExcelParseAppServiceTest,QuestionBankRpcServiceTest,ExcelParserUtilTest test` in `eaqb-excel-parser`.
- [ ] Step 3: Run `mvn -q -DskipTests compile` at repo root or affected modules if mapper/resource wiring changed.
- [ ] Step 4: Summarize new contract, known residual risk, and any TODOs such as batch cleanup job implementation.
