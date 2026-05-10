package com.zhoushuo.eaqb.question.bank.biz.service.impl.imports;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionImportBatchDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionImportTempDO;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionImportBatchStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.question.bank.req.AppendImportChunkRequestDTO;
import com.zhoushuo.eaqb.question.bank.resp.CommitImportBatchResponseDTO;
import com.zhoushuo.framework.common.exception.BizException;
import com.zhoushuo.framework.common.response.Response;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 导入流程门面：
 * 对外提供统一编排入口，内部委托给细粒度组件执行。
 */
@Component
public class ImportWorkflowFacade {

    private final ImportChunkRequestValidator importChunkRequestValidator;
    private final ImportChunkHashValidator importChunkHashValidator;
    private final ImportChunkDecisionService importChunkDecisionService;
    private final ImportBatchAssembler importBatchAssembler;
    private final ImportBatchStateMachine importBatchStateMachine;
    private final ImportBatchCommitExecutor importBatchCommitExecutor;

    public ImportWorkflowFacade(ImportChunkRequestValidator importChunkRequestValidator,
                                ImportChunkHashValidator importChunkHashValidator,
                                ImportChunkDecisionService importChunkDecisionService,
                                ImportBatchAssembler importBatchAssembler,
                                ImportBatchStateMachine importBatchStateMachine,
                                ImportBatchCommitExecutor importBatchCommitExecutor) {
        this.importChunkRequestValidator = importChunkRequestValidator;
        this.importChunkHashValidator = importChunkHashValidator;
        this.importChunkDecisionService = importChunkDecisionService;
        this.importBatchAssembler = importBatchAssembler;
        this.importBatchStateMachine = importBatchStateMachine;
        this.importBatchCommitExecutor = importBatchCommitExecutor;
    }

    public void validateAppendRequest(AppendImportChunkRequestDTO request) {
        importChunkRequestValidator.validate(request);
    }

    public void requireStatus(QuestionImportBatchDO batch, QuestionImportBatchStatusEnum expectedStatus) {
        importBatchStateMachine.requireStatus(batch, expectedStatus);
    }

    /**
     * 下游重新计算 contentHash，防止 payload 被篡改或序列化差异导致不一致。
     */
    public void ensureChunkHashMatchesPayload(QuestionImportBatchDO batch, AppendImportChunkRequestDTO request) {
        if (importChunkHashValidator.isPayloadHashMatched(request)) {
            return;
        }
        importBatchStateMachine.markFailedByWriter(batch.getId(), QuestionImportBatchStatusEnum.APPENDING,
                "chunk hash mismatch, chunkNo=" + request.getChunkNo());
        throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_CHUNK_CONFLICT.getErrorCode(),
                "chunk hash mismatch, chunkNo=" + request.getChunkNo());
    }

    public ImportChunkDecision decideChunk(AppendImportChunkRequestDTO request, QuestionImportTempDO existingChunk) {
        return importChunkDecisionService.decide(request, existingChunk);
    }

    public List<QuestionImportTempDO> toTempRows(AppendImportChunkRequestDTO request) {
        return importBatchAssembler.toTempRows(request);
    }

    public void markFailedByWriter(Long batchId, QuestionImportBatchStatusEnum expectedStatus, String errorMessage) {
        importBatchStateMachine.markFailedByWriter(batchId, expectedStatus, errorMessage);
    }

    public void markFailedByMapper(Long batchId, QuestionImportBatchStatusEnum expectedStatus, String errorMessage) {
        importBatchStateMachine.markFailedByMapper(batchId, expectedStatus, errorMessage);
    }

    public void markReadyOrThrow(Long batchId, int expectedChunkCount, int expectedRowCount) {
        importBatchStateMachine.markReadyOrThrow(batchId, expectedChunkCount, expectedRowCount);
    }

    public Response<CommitImportBatchResponseDTO> commit(QuestionImportBatchDO batch,
                                                         List<QuestionImportTempDO> tempRows,
                                                         List<Long> questionIds) {
        return importBatchCommitExecutor.commit(batch, tempRows, questionIds);
    }
}