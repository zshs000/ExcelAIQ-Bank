package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionImportBatchDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionImportTempDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionImportBatchDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionImportTempDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionImportBatchStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.question.bank.biz.rpc.DistributedIdGeneratorRpcService;
import com.zhoushuo.eaqb.question.bank.biz.service.impl.imports.ImportBatchAssembler;
import com.zhoushuo.eaqb.question.bank.biz.service.impl.imports.ImportBatchCommitExecutor;
import com.zhoushuo.eaqb.question.bank.biz.service.impl.imports.ImportBatchStateMachine;
import com.zhoushuo.eaqb.question.bank.biz.service.impl.imports.ImportChunkDecision;
import com.zhoushuo.eaqb.question.bank.biz.service.impl.imports.ImportChunkDecisionService;
import com.zhoushuo.eaqb.question.bank.biz.service.impl.imports.ImportChunkHashValidator;
import com.zhoushuo.eaqb.question.bank.biz.service.impl.imports.ImportChunkRequestValidator;
import com.zhoushuo.eaqb.question.bank.req.AppendImportChunkRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.CommitImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.CreateImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.FinishImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.resp.AppendImportChunkResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.CommitImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.CreateImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.FinishImportBatchResponseDTO;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class QuestionImportBatchAppService {

    @Resource
    private QuestionImportBatchDOMapper questionImportBatchDOMapper;
    @Resource
    private QuestionImportTempDOMapper questionImportTempDOMapper;
    @Resource
    private QuestionDOMapper questionDOMapper;
    @Resource
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Resource
    private QuestionAccessSupport questionAccessSupport;
    @Resource
    private QuestionImportBatchStatusWriter questionImportBatchStatusWriter;
    @Resource
    private TransactionTemplate transactionTemplate;

    private final ImportChunkRequestValidator importChunkRequestValidator = new ImportChunkRequestValidator();
    private final ImportChunkHashValidator importChunkHashValidator = new ImportChunkHashValidator();
    private final ImportChunkDecisionService importChunkDecisionService = new ImportChunkDecisionService();
    private final ImportBatchAssembler importBatchAssembler = new ImportBatchAssembler();

    /**
     * 创建导入批次，进入 APPENDING 状态，后续只允许追加分块。
     */
    public Response<CreateImportBatchResponseDTO> createImportBatch(CreateImportBatchRequestDTO request) {
        if (request == null || request.getFileId() == null || request.getChunkSize() == null || request.getChunkSize() <= 0) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }

        // 批次与用户强绑定，后续 append/finish/commit 都会做归属校验。
        Long currentUserId = questionAccessSupport.requireCurrentUserId();
        // 批次ID由分布式ID服务生成，避免并发场景下主键冲突。
        Long batchId = Long.valueOf(distributedIdGeneratorRpcService.nextQuestionBankEntityId());
        LocalDateTime now = LocalDateTime.now();
        QuestionImportBatchDO batch = QuestionImportBatchDO.builder()
                .id(batchId)
                .fileId(request.getFileId())
                .userId(currentUserId)
                // 新建批次默认进入 APPENDING，只允许接收分块，不允许直接 commit。
                .status(QuestionImportBatchStatusEnum.APPENDING.getCode())
                .chunkSize(request.getChunkSize())
                // 计数从 0 起步，后续由 append 成功时原子累加。
                .receivedChunkCount(0)
                .totalRowCount(0)
                .createdTime(now)
                .updatedTime(now)
                .build();
        // insertSelective <= 0 代表插入失败（例如SQL执行异常或影响行数不符合预期）。
        if (questionImportBatchDOMapper.insertSelective(batch) <= 0) {
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_CREATE_FAILED);
        }

        return Response.success(CreateImportBatchResponseDTO.builder()
                .batchId(batchId)
                .status(QuestionImportBatchStatusEnum.APPENDING.getCode())
                .build());
    }

    /**
     * 追加一个分块：
     * 1. 参数校验
     * 2. 批次归属/状态校验
     * 3. 内容哈希校验
     * 4. 幂等判定（重复/冲突/新写入）
     * 5. 落库并累计批次计数
     */
    @Transactional(rollbackFor = Exception.class)
    public Response<AppendImportChunkResponseDTO> appendImportChunk(AppendImportChunkRequestDTO request) {
        // 第1步：校验请求基础字段与行数一致性，避免脏请求进入业务流程。
        importChunkRequestValidator.validate(request);

        // 第2步：校验批次归属和状态，只允许批次所有者在 APPENDING 阶段追加分块。
        QuestionImportBatchDO batch = requireOwnedBatch(request.getBatchId());
        ImportBatchStateMachine stateMachine = importBatchStateMachine();
        stateMachine.requireStatus(batch, QuestionImportBatchStatusEnum.APPENDING);

        // 第3步：下游重算 hash 做报文完整性校验，防止请求体与 contentHash 不一致。
        ensureChunkHashMatchesPayload(batch, request, stateMachine);

        // 第4步：按 (batchId, chunkNo) 判断本次是重复重试、冲突重试还是首次写入。
        QuestionImportTempDO existingChunk = questionImportTempDOMapper.selectChunkMeta(request.getBatchId(), request.getChunkNo());
        ImportChunkDecision decision = importChunkDecisionService.decide(request, existingChunk);
        if (decision == ImportChunkDecision.DUPLICATE) {
            // 重复重试且内容一致：直接返回幂等成功，不重复写临时表。
            return buildDuplicateResponse(batch, request);
        }
        if (decision == ImportChunkDecision.CONFLICT) {
            // 重试内容与历史分块不一致：冻结批次并抛出业务冲突异常。
            stateMachine.markFailedByWriter(batch.getId(), QuestionImportBatchStatusEnum.APPENDING,
                    "chunk payload drift detected, chunkNo=" + request.getChunkNo());
            throw bizException(ResponseCodeEnum.QUESTION_IMPORT_CHUNK_CONFLICT.getErrorCode(),
                    "chunk重试内容不一致, chunkNo=" + request.getChunkNo());
        }

        // 第5步：新分块落临时表，并原子累加批次计数（chunk 数 + 行数）。
        List<QuestionImportTempDO> rows = importBatchAssembler.toTempRows(request);
        if (questionImportTempDOMapper.batchInsert(rows) != rows.size()) {
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_CHUNK_APPEND_FAILED);
        }
        if (questionImportBatchDOMapper.increaseAfterChunkAccepted(batch.getId(),
                QuestionImportBatchStatusEnum.APPENDING.getCode(), 1, rows.size()) <= 0) {
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_STATUS_ILLEGAL);
        }

        // 第6步：返回“分块接收成功”响应。
        return buildAcceptedResponse(batch, request, rows.size());
    }

    /**
     * 结束追加阶段，核对调用方上报计数与服务端累计计数一致后，流转到 READY。
     * 执行顺序：
     * 1. 校验 finish 请求参数；
     * 2. 校验批次归属和当前状态（必须是 APPENDING）；
     * 3. 对账 expectedChunkCount/expectedRowCount 与服务端累计计数；
     * 4. 对账通过则更新为 READY，失败则标记 FAILED。
     */
    public Response<FinishImportBatchResponseDTO> finishImportBatch(FinishImportBatchRequestDTO request) {
        // 第1步：finish 请求参数校验。
        validateFinishRequest(request);

        // 第2步：校验批次归属和状态。
        QuestionImportBatchDO batch = requireOwnedBatch(request.getBatchId());
        ImportBatchStateMachine stateMachine = importBatchStateMachine();
        stateMachine.requireStatus(batch, QuestionImportBatchStatusEnum.APPENDING);

        // 第3步：调用方上报计数与服务端累计计数必须一致。
        if (!request.getExpectedChunkCount().equals(batch.getReceivedChunkCount())
                || !request.getExpectedRowCount().equals(batch.getTotalRowCount())) {
            // 对账失败：冻结批次，防止脏数据继续进入 commit。
            stateMachine.markFailedByMapper(batch.getId(), QuestionImportBatchStatusEnum.APPENDING,
                    "finish batch count mismatch");
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_COUNT_MISMATCH);
        }

        // 第4步：对账通过，流转 READY。
        stateMachine.markReadyOrThrow(batch.getId(), request.getExpectedChunkCount(), request.getExpectedRowCount());

        return Response.success(FinishImportBatchResponseDTO.builder()
                .batchId(batch.getId())
                .status(QuestionImportBatchStatusEnum.READY.getCode())
                .expectedChunkCount(request.getExpectedChunkCount())
                .totalRowCount(request.getExpectedRowCount())
                .build());
    }

    /**
     * 正式提交导入：
     * 执行顺序：
     * 1. 校验 commit 请求参数；
     * 2. 校验批次归属和状态（必须是 READY）；
     * 3. 读取临时明细并做行数对账；
     * 4. 申请正式题目 ID；
     * 5. 在事务中执行“转正写入 + 批次状态流转”；
     * 6. 返回提交结果。
     */
    public Response<CommitImportBatchResponseDTO> commitImportBatch(CommitImportBatchRequestDTO request) {
        // 第1步：commit 请求参数校验。
        validateCommitRequest(request);

        // 第2步：校验批次归属和状态（READY 才允许提交）。
        QuestionImportBatchDO batch = requireOwnedBatch(request.getBatchId());
        ImportBatchStateMachine stateMachine = importBatchStateMachine();
        stateMachine.requireStatus(batch, QuestionImportBatchStatusEnum.READY);

        // 第3步：读取临时明细并校验总行数，防止临时表缺失或脏数据提交。
        List<QuestionImportTempDO> tempRows = questionImportTempDOMapper.selectByBatchIdOrderByChunkNoAndRowNo(batch.getId());
        if (tempRows == null || tempRows.isEmpty() || tempRows.size() != batch.getTotalRowCount()) {
            // 对账失败：标记 FAILED，阻断后续提交。
            stateMachine.markFailedByMapper(batch.getId(), QuestionImportBatchStatusEnum.READY,
                    "commit batch row count mismatch");
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_COUNT_MISMATCH);
        }

        // 第4步：按临时行数批量申请正式题目 ID。
        List<Long> questionIds = distributedIdGeneratorRpcService.nextQuestionBankEntityIds(tempRows.size());

        // 第5步：在事务中执行“临时行转正式题目 + 批次状态更新为 COMMITTED”。
        ImportBatchCommitExecutor commitExecutor =
                new ImportBatchCommitExecutor(questionDOMapper, stateMachine, importBatchAssembler);

        // 第6步：返回提交结果。
        return transactionTemplate.execute(status -> commitExecutor.commit(batch, tempRows, questionIds));
    }

    /**
     * 旧分块重试且内容一致，直接返回幂等成功，不重复写库。
     */
    private Response<AppendImportChunkResponseDTO> buildDuplicateResponse(QuestionImportBatchDO batch,
                                                                          AppendImportChunkRequestDTO request) {
        return Response.success(AppendImportChunkResponseDTO.builder()
                .batchId(batch.getId())
                .chunkNo(request.getChunkNo())
                .duplicateChunk(true)
                .receivedChunkCount(batch.getReceivedChunkCount())
                .totalRowCount(batch.getTotalRowCount())
                .build());
    }

    /**
     * 新分块写入成功后的标准响应。
     */
    private Response<AppendImportChunkResponseDTO> buildAcceptedResponse(QuestionImportBatchDO batch,
                                                                         AppendImportChunkRequestDTO request,
                                                                         int acceptedRows) {
        return Response.success(AppendImportChunkResponseDTO.builder()
                .batchId(batch.getId())
                .chunkNo(request.getChunkNo())
                .duplicateChunk(false)
                .receivedChunkCount(batch.getReceivedChunkCount() + 1)
                .totalRowCount(batch.getTotalRowCount() + acceptedRows)
                .build());
    }

    /**
     * finish 阶段基础参数校验。
     * 这里只做“是否为空”校验；计数对账在 finishImportBatch 主流程中完成。
     */
    private void validateFinishRequest(FinishImportBatchRequestDTO request) {
        if (request == null || request.getBatchId() == null
                || request.getExpectedChunkCount() == null || request.getExpectedRowCount() == null) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }
    }

    /**
     * commit 阶段基础参数校验。
     * commit 只需要 batchId，其他约束（状态/临时行完整性）在主流程中校验。
     */
    private void validateCommitRequest(CommitImportBatchRequestDTO request) {
        if (request == null || request.getBatchId() == null) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }
    }

    /**
     * 下游对上游传入的分块内容做二次哈希校验，防止报文被篡改或序列化漂移。
     */
    private void ensureChunkHashMatchesPayload(QuestionImportBatchDO batch,
                                               AppendImportChunkRequestDTO request,
                                               ImportBatchStateMachine stateMachine) {
        if (importChunkHashValidator.isPayloadHashMatched(request)) {
            return;
        }
        stateMachine.markFailedByWriter(batch.getId(), QuestionImportBatchStatusEnum.APPENDING,
                "chunk hash mismatch, chunkNo=" + request.getChunkNo());
        throw bizException(ResponseCodeEnum.QUESTION_IMPORT_CHUNK_CONFLICT.getErrorCode(),
                "chunk hash mismatch, chunkNo=" + request.getChunkNo());
    }

    /**
     * 查询并返回批次，同时完成两类校验：
     * 1. 批次存在性校验（不存在则抛 NOT_FOUND）；
     * 2. 批次归属校验（非 owner 则抛 NO_PERMISSION）。
     */
    private QuestionImportBatchDO requireOwnedBatch(Long batchId) {
        // 先查批次实体，后续状态机和计数逻辑都依赖这个快照。
        QuestionImportBatchDO batch = questionImportBatchDOMapper.selectByPrimaryKey(batchId);
        if (batch == null) {
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_NOT_FOUND);
        }
        // 再校验当前登录用户是否为批次 owner。
        Long currentUserId = questionAccessSupport.requireCurrentUserId();
        if (!currentUserId.equals(batch.getUserId())) {
            throw new BizException(ResponseCodeEnum.NO_PERMISSION);
        }
        return batch;
    }

    /**
     * 状态机封装按需构建，统一状态校验与状态流转写操作。
     */
    private ImportBatchStateMachine importBatchStateMachine() {
        return new ImportBatchStateMachine(questionImportBatchDOMapper, questionImportBatchStatusWriter);
    }

    private BizException bizException(String errorCode, String errorMessage) {
        return new BizException(errorCode, errorMessage);
    }
}
