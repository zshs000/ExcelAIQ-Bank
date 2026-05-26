package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionImportBatchDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionImportTempDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionImportBatchDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionImportTempDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.model.QuestionImportFormalIdBinding;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionImportBatchStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.question.bank.biz.rpc.DistributedIdGeneratorRpcService;
import com.zhoushuo.eaqb.question.bank.biz.service.impl.imports.ImportChunkDecision;
import com.zhoushuo.eaqb.question.bank.biz.service.impl.imports.ImportWorkflowFacade;
import com.zhoushuo.eaqb.question.bank.req.AppendImportChunkRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.AbortImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.CommitImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.CreateImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.FindImportBatchByFileRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.FinishImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.resp.AppendImportChunkResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.CommitImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.CreateImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.FindImportBatchByFileResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.FinishImportBatchResponseDTO;
import com.zhoushuo.framework.common.exception.BizException;
import com.zhoushuo.framework.common.response.Response;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class QuestionImportBatchAppService {

    private static final int FORMAL_ID_BIND_PAGE_SIZE = 1000;

    @Resource
    private QuestionImportBatchDOMapper questionImportBatchDOMapper;
    @Resource
    private QuestionImportTempDOMapper questionImportTempDOMapper;
    @Resource
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Resource
    private QuestionAccessSupport questionAccessSupport;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private ImportWorkflowFacade importWorkflowFacade;

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

    public Response<FindImportBatchByFileResponseDTO> findImportBatchByFile(FindImportBatchByFileRequestDTO request) {
        if (request == null || request.getFileId() == null) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }
        Long currentUserId = questionAccessSupport.requireCurrentUserId();
        List<QuestionImportBatchDO> batches = questionImportBatchDOMapper
                .selectRecoverableByFileIdAndUserId(request.getFileId(), currentUserId);
        if (batches == null || batches.isEmpty()) {
            return Response.success(FindImportBatchByFileResponseDTO.builder()
                    .found(false)
                    .build());
        }
        QuestionImportBatchDO batch = batches.get(0);
        return Response.success(FindImportBatchByFileResponseDTO.builder()
                .found(true)
                .batchId(batch.getId())
                .status(batch.getStatus())
                .totalRowCount(batch.getTotalRowCount())
                .importedCount(batch.getImportedCount())
                .build());
    }

    public Response<Void> abortImportBatch(AbortImportBatchRequestDTO request) {
        if (request == null || request.getBatchId() == null) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }
        return abortAppendingImportBatch(request.getBatchId(), request.getReason());
    }

    public Response<Void> abortAppendingImportBatch(Long batchId, String reason) {
        QuestionImportBatchDO batch = requireOwnedBatch(batchId);
        importWorkflowFacade.requireStatus(batch, QuestionImportBatchStatusEnum.APPENDING);
        if (questionImportBatchDOMapper.markAborted(batchId,
                QuestionImportBatchStatusEnum.APPENDING.getCode(), reason) <= 0) {
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_STATUS_ILLEGAL);
        }
        return Response.success();
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
        importWorkflowFacade.validateAppendRequest(request);

        // 第2步：校验批次归属和状态，只允许批次所有者在 APPENDING 阶段追加分块。
        QuestionImportBatchDO batch = requireOwnedBatch(request.getBatchId());
        importWorkflowFacade.requireStatus(batch, QuestionImportBatchStatusEnum.APPENDING);

        // 第3步：下游重算 hash 做报文完整性校验，防止请求体与 contentHash 不一致。
        importWorkflowFacade.ensureChunkHashMatchesPayload(batch, request);

        // 第4步：按 (batchId, chunkNo) 判断本次是重复重试、冲突重试还是首次写入。
        QuestionImportTempDO existingChunk = questionImportTempDOMapper.selectChunkMeta(request.getBatchId(), request.getChunkNo());
        ImportChunkDecision decision = importWorkflowFacade.decideChunk(request, existingChunk);
        if (decision == ImportChunkDecision.DUPLICATE) {
            // 重复重试且内容一致：直接返回幂等成功，不重复写临时表。
            return Response.success(AppendImportChunkResponseDTO.builder()
                    .batchId(batch.getId())
                    .chunkNo(request.getChunkNo())
                    .duplicateChunk(true)
                    .receivedChunkCount(batch.getReceivedChunkCount())
                    .totalRowCount(batch.getTotalRowCount())
                    .build());
        }
        if (decision == ImportChunkDecision.CONFLICT) {
            // 重试内容与历史分块不一致：冻结批次并抛出业务冲突异常。
            importWorkflowFacade.markFailedByWriter(batch.getId(), QuestionImportBatchStatusEnum.APPENDING,
                    "chunk payload drift detected, chunkNo=" + request.getChunkNo());
            throw bizException(ResponseCodeEnum.QUESTION_IMPORT_CHUNK_CONFLICT.getErrorCode(),
                    "chunk重试内容不一致, chunkNo=" + request.getChunkNo());
        }

        // 第5步：新分块落临时表，并原子累加批次计数（chunk 数 + 行数）。
        List<QuestionImportTempDO> rows = importWorkflowFacade.toTempRows(request);
        if (questionImportTempDOMapper.batchInsert(rows) != rows.size()) {
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_CHUNK_APPEND_FAILED);
        }
        if (questionImportBatchDOMapper.increaseAfterChunkAccepted(batch.getId(),
                QuestionImportBatchStatusEnum.APPENDING.getCode(), 1, rows.size()) <= 0) {
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_STATUS_ILLEGAL);
        }

        // 第6步：返回“分块接收成功”响应。
        return Response.success(AppendImportChunkResponseDTO.builder()
                .batchId(batch.getId())
                .chunkNo(request.getChunkNo())
                .duplicateChunk(false)
                .receivedChunkCount(batch.getReceivedChunkCount() + 1)
                .totalRowCount(batch.getTotalRowCount() + rows.size())
                .build());
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
        importWorkflowFacade.requireStatus(batch, QuestionImportBatchStatusEnum.APPENDING);

        // 第3步：调用方上报计数与服务端累计计数必须一致。
        if (!request.getExpectedChunkCount().equals(batch.getReceivedChunkCount())
                || !request.getExpectedRowCount().equals(batch.getTotalRowCount())) {
            // 对账失败：冻结批次，防止脏数据继续进入 commit。
            importWorkflowFacade.markFailedByMapper(batch.getId(), QuestionImportBatchStatusEnum.APPENDING,
                    "finish batch count mismatch");
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_COUNT_MISMATCH);
        }

        bindFormalIdsOrFail(batch.getId(), batch.getTotalRowCount(), QuestionImportBatchStatusEnum.APPENDING);

        // 第4步：对账通过，流转 READY。
        importWorkflowFacade.markReadyOrThrow(batch.getId(), request.getExpectedChunkCount(), request.getExpectedRowCount());

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
     * 3. 校验临时明细行数和 formal_id 绑定完整性；
     * 4. 在事务中执行“数据库内转正 + 批次状态流转”；
     * 5. 返回提交结果。
     */
    public Response<CommitImportBatchResponseDTO> commitImportBatch(CommitImportBatchRequestDTO request) {
        // 第1步：commit 请求参数校验。
        validateCommitRequest(request);

        // 第2步：校验批次归属和状态（READY 才允许提交）。
        QuestionImportBatchDO batch = requireOwnedBatch(request.getBatchId());
        if (QuestionImportBatchStatusEnum.COMMITTED.getCode().equals(batch.getStatus())) {
            return Response.success(CommitImportBatchResponseDTO.builder()
                    .batchId(batch.getId())
                    .status(QuestionImportBatchStatusEnum.COMMITTED.getCode())
                    .importedCount(batch.getImportedCount())
                    .build());
        }
        importWorkflowFacade.requireStatus(batch, QuestionImportBatchStatusEnum.READY);

        CommitTransactionOutcome outcome = transactionTemplate.execute(status -> commitLocked(request.getBatchId()));
        if (outcome == null) {
            throw new BizException(ResponseCodeEnum.SYSTEM_ERROR);
        }
        if (outcome.failure != null) {
            throw outcome.failure;
        }
        return outcome.response;
    }

    private CommitTransactionOutcome commitLocked(Long batchId) {
        QuestionImportBatchDO batch = requireOwnedBatchForUpdate(batchId);
        if (QuestionImportBatchStatusEnum.COMMITTED.getCode().equals(batch.getStatus())) {
            return CommitTransactionOutcome.success(Response.success(CommitImportBatchResponseDTO.builder()
                    .batchId(batch.getId())
                    .status(QuestionImportBatchStatusEnum.COMMITTED.getCode())
                    .importedCount(batch.getImportedCount())
                    .build()));
        }
        importWorkflowFacade.requireStatus(batch, QuestionImportBatchStatusEnum.READY);

        int tempRowCount = questionImportTempDOMapper.countByBatchId(batch.getId());
        if (tempRowCount <= 0 || tempRowCount != batch.getTotalRowCount()) {
            importWorkflowFacade.markFailedByMapper(batch.getId(), QuestionImportBatchStatusEnum.READY,
                    "commit batch row count mismatch");
            return CommitTransactionOutcome.failure(new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_COUNT_MISMATCH));
        }
        if (!formalIdsFullyBound(batch.getId(), batch.getTotalRowCount())) {
            importWorkflowFacade.markFailedByMapper(batch.getId(), QuestionImportBatchStatusEnum.READY,
                    "formal id bind check failed");
            return CommitTransactionOutcome.failure(new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_COUNT_MISMATCH));
        }

        return CommitTransactionOutcome.success(importWorkflowFacade.commit(batch));
    }

    private void bindFormalIdsOrFail(Long batchId, int totalRowCount, QuestionImportBatchStatusEnum expectedStatus) {
        int maxIterations = (totalRowCount / FORMAL_ID_BIND_PAGE_SIZE) + 2;
        int iterations = 0;

        while (true) {
            if (++iterations > maxIterations) {
                importWorkflowFacade.markFailedByMapper(batchId, expectedStatus, "formal id bind iteration overflow");
                throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_COUNT_MISMATCH);
            }
            List<Long> tempIds = questionImportTempDOMapper.selectUnboundIdsByBatchId(batchId, FORMAL_ID_BIND_PAGE_SIZE);
            if (tempIds == null || tempIds.isEmpty()) {
                break;
            }
            List<Long> formalIds = nextFormalIdsOrFailBatch(batchId, tempIds.size(), expectedStatus);
            List<QuestionImportFormalIdBinding> bindings = new java.util.ArrayList<>(tempIds.size());
            for (int i = 0; i < tempIds.size(); i++) {
                bindings.add(new QuestionImportFormalIdBinding(tempIds.get(i), formalIds.get(i)));
            }
            int updated = questionImportTempDOMapper.bindFormalIds(batchId, bindings);
            if (updated != bindings.size()) {
                importWorkflowFacade.markFailedByMapper(batchId, expectedStatus, "formal id bind incomplete");
                throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_COUNT_MISMATCH);
            }
        }
        ensureFormalIdsBoundOrFail(batchId, totalRowCount, expectedStatus);
    }

    private List<Long> nextFormalIdsOrFailBatch(Long batchId, int count, QuestionImportBatchStatusEnum expectedStatus) {
        try {
            return distributedIdGeneratorRpcService.nextQuestionBankEntityIds(count);
        } catch (BizException ex) {
            importWorkflowFacade.markFailedByMapper(batchId, expectedStatus, "formal id generate failed");
            throw ex;
        }
    }

    // 最终状态边界校验：finish 后保证 READY 前已全部绑定，commit 前再次防止脏数据转正。
    private void ensureFormalIdsBoundOrFail(Long batchId, int totalRowCount, QuestionImportBatchStatusEnum expectedStatus) {
        if (!formalIdsFullyBound(batchId, totalRowCount)) {
            importWorkflowFacade.markFailedByMapper(batchId, expectedStatus, "formal id bind check failed");
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_COUNT_MISMATCH);
        }
    }

    private boolean formalIdsFullyBound(Long batchId, int totalRowCount) {
        return questionImportTempDOMapper.countUnboundFormalId(batchId) == 0
                && questionImportTempDOMapper.countDistinctFormalId(batchId) == totalRowCount;
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
     * 查询并返回批次，同时完成两类校验：
     * 1. 批次存在性校验（不存在则抛 NOT_FOUND）；
     * 2. 批次归属校验（非 owner 则抛 NO_PERMISSION）。
     */
    private QuestionImportBatchDO requireOwnedBatch(Long batchId) {
        // 先查批次实体，后续状态机和计数逻辑都依赖这个快照。
        QuestionImportBatchDO batch = questionImportBatchDOMapper.selectByPrimaryKey(batchId);
        return requireOwnedBatch(batch);
    }

    private QuestionImportBatchDO requireOwnedBatchForUpdate(Long batchId) {
        QuestionImportBatchDO batch = questionImportBatchDOMapper.selectByPrimaryKeyForUpdate(batchId);
        return requireOwnedBatch(batch);
    }

    private QuestionImportBatchDO requireOwnedBatch(QuestionImportBatchDO batch) {
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

    private BizException bizException(String errorCode, String errorMessage) {
        return new BizException(errorCode, errorMessage);
    }

    private static class CommitTransactionOutcome {
        private final Response<CommitImportBatchResponseDTO> response;
        private final BizException failure;

        private CommitTransactionOutcome(Response<CommitImportBatchResponseDTO> response, BizException failure) {
            this.response = response;
            this.failure = failure;
        }

        private static CommitTransactionOutcome success(Response<CommitImportBatchResponseDTO> response) {
            return new CommitTransactionOutcome(response, null);
        }

        private static CommitTransactionOutcome failure(BizException failure) {
            return new CommitTransactionOutcome(null, failure);
        }
    }
}
