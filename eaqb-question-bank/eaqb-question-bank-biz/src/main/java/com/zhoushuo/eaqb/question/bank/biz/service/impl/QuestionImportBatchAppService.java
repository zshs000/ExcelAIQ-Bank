package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionImportBatchDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionImportTempDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionImportBatchDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionImportTempDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionImportBatchStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionProcessStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.question.bank.biz.rpc.DistributedIdGeneratorRpcService;
import com.zhoushuo.eaqb.question.bank.req.AppendImportChunkRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.CommitImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.CreateImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.FinishImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.ImportQuestionRowDTO;
import com.zhoushuo.eaqb.question.bank.resp.AppendImportChunkResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.CommitImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.CreateImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.FinishImportBatchResponseDTO;
import com.zhoushuo.framework.commono.exception.BaseExceptionInterface;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    public Response<CreateImportBatchResponseDTO> createImportBatch(CreateImportBatchRequestDTO request) {
        if (request == null || request.getFileId() == null || request.getChunkSize() == null || request.getChunkSize() <= 0) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }

        Long currentUserId = questionAccessSupport.requireCurrentUserId();
        Long batchId = Long.valueOf(distributedIdGeneratorRpcService.nextQuestionBankEntityId());
        LocalDateTime now = LocalDateTime.now();
        QuestionImportBatchDO batch = QuestionImportBatchDO.builder()
                .id(batchId)
                .fileId(request.getFileId())
                .userId(currentUserId)
                .status(QuestionImportBatchStatusEnum.APPENDING.getCode())
                .chunkSize(request.getChunkSize())
                .receivedChunkCount(0)
                .totalRowCount(0)
                .createdTime(now)
                .updatedTime(now)
                .build();
        if (questionImportBatchDOMapper.insertSelective(batch) <= 0) {
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_CREATE_FAILED);
        }

        return Response.success(CreateImportBatchResponseDTO.builder()
                .batchId(batchId)
                .status(QuestionImportBatchStatusEnum.APPENDING.getCode())
                .build());
    }

    @Transactional(rollbackFor = Exception.class)
    public Response<AppendImportChunkResponseDTO> appendImportChunk(AppendImportChunkRequestDTO request) {
        validateAppendRequest(request);

        QuestionImportBatchDO batch = requireOwnedBatch(request.getBatchId());
        requireStatus(batch, QuestionImportBatchStatusEnum.APPENDING);

        QuestionImportTempDO existingChunk = questionImportTempDOMapper.selectChunkMeta(request.getBatchId(), request.getChunkNo());
        if (existingChunk != null) {
            if (request.getRowCount().equals(existingChunk.getChunkRowCount())
                    && StringUtils.equals(request.getContentHash(), existingChunk.getContentHash())) {
                return Response.success(AppendImportChunkResponseDTO.builder()
                        .batchId(batch.getId())
                        .chunkNo(request.getChunkNo())
                        .duplicateChunk(true)
                        .receivedChunkCount(batch.getReceivedChunkCount())
                        .totalRowCount(batch.getTotalRowCount())
                        .build());
            }
            questionImportBatchStatusWriter.markFailed(batch.getId(), QuestionImportBatchStatusEnum.APPENDING.getCode(),
                    "chunk payload drift detected, chunkNo=" + request.getChunkNo());
            throw bizException(ResponseCodeEnum.QUESTION_IMPORT_CHUNK_CONFLICT.getErrorCode(),
                    "chunk 重试内容不一致, chunkNo=" + request.getChunkNo());
        }

        List<QuestionImportTempDO> rows = buildTempRows(request);
        if (questionImportTempDOMapper.batchInsert(rows) != rows.size()) {
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_CHUNK_APPEND_FAILED);
        }
        if (questionImportBatchDOMapper.increaseAfterChunkAccepted(batch.getId(),
                QuestionImportBatchStatusEnum.APPENDING.getCode(), 1, rows.size()) <= 0) {
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_STATUS_ILLEGAL);
        }

        return Response.success(AppendImportChunkResponseDTO.builder()
                .batchId(batch.getId())
                .chunkNo(request.getChunkNo())
                .duplicateChunk(false)
                .receivedChunkCount(batch.getReceivedChunkCount() + 1)
                .totalRowCount(batch.getTotalRowCount() + rows.size())
                .build());
    }

    public Response<FinishImportBatchResponseDTO> finishImportBatch(FinishImportBatchRequestDTO request) {
        if (request == null || request.getBatchId() == null
                || request.getExpectedChunkCount() == null || request.getExpectedRowCount() == null) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }

        QuestionImportBatchDO batch = requireOwnedBatch(request.getBatchId());
        requireStatus(batch, QuestionImportBatchStatusEnum.APPENDING);
        if (!request.getExpectedChunkCount().equals(batch.getReceivedChunkCount())
                || !request.getExpectedRowCount().equals(batch.getTotalRowCount())) {
            questionImportBatchDOMapper.markFailed(batch.getId(), QuestionImportBatchStatusEnum.APPENDING.getCode(),
                    "finish batch count mismatch");
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_COUNT_MISMATCH);
        }
        if (questionImportBatchDOMapper.markReady(batch.getId(),
                QuestionImportBatchStatusEnum.APPENDING.getCode(),
                request.getExpectedChunkCount(),
                request.getExpectedRowCount()) <= 0) {
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_STATUS_ILLEGAL);
        }

        return Response.success(FinishImportBatchResponseDTO.builder()
                .batchId(batch.getId())
                .status(QuestionImportBatchStatusEnum.READY.getCode())
                .expectedChunkCount(request.getExpectedChunkCount())
                .totalRowCount(request.getExpectedRowCount())
                .build());
    }

    public Response<CommitImportBatchResponseDTO> commitImportBatch(CommitImportBatchRequestDTO request) {
        if (request == null || request.getBatchId() == null) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }

        QuestionImportBatchDO batch = requireOwnedBatch(request.getBatchId());
        requireStatus(batch, QuestionImportBatchStatusEnum.READY);

        List<QuestionImportTempDO> tempRows = questionImportTempDOMapper.selectByBatchIdOrderByChunkNoAndRowNo(batch.getId());
        if (tempRows == null || tempRows.isEmpty() || tempRows.size() != batch.getTotalRowCount()) {
            questionImportBatchDOMapper.markFailed(batch.getId(), QuestionImportBatchStatusEnum.READY.getCode(),
                    "commit batch row count mismatch");
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_COUNT_MISMATCH);
        }

        List<Long> questionIds = distributedIdGeneratorRpcService.nextQuestionBankEntityIds(tempRows.size());

        return transactionTemplate.execute(status -> commitImportBatchInTransaction(batch, tempRows, questionIds));
    }

    @Transactional(rollbackFor = Exception.class)
    protected Response<CommitImportBatchResponseDTO> commitImportBatchInTransaction(QuestionImportBatchDO batch,
                                                                                     List<QuestionImportTempDO> tempRows,
                                                                                     List<Long> questionIds) {
        LocalDateTime now = LocalDateTime.now();
        List<QuestionDO> questions = new ArrayList<>(tempRows.size());
        for (int i = 0; i < tempRows.size(); i++) {
            QuestionImportTempDO tempRow = tempRows.get(i);
            questions.add(QuestionDO.builder()
                    .id(questionIds.get(i))
                    .content(tempRow.getContent())
                    .answer(tempRow.getAnswer())
                    .analysis(tempRow.getAnalysis())
                    .processStatus(QuestionProcessStatusEnum.WAITING.getCode())
                    .createdBy(batch.getUserId())
                    .createdTime(now)
                    .updatedTime(now)
                    .build());
        }

        if (questionDOMapper.batchInsert(questions) != questions.size()) {
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_COMMIT_FAILED);
        }
        if (questionImportBatchDOMapper.markCommitted(batch.getId(),
                QuestionImportBatchStatusEnum.READY.getCode(),
                questions.size()) <= 0) {
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_STATUS_ILLEGAL);
        }

        return Response.success(CommitImportBatchResponseDTO.builder()
                .batchId(batch.getId())
                .status(QuestionImportBatchStatusEnum.COMMITTED.getCode())
                .importedCount(questions.size())
                .build());
    }

    private void validateAppendRequest(AppendImportChunkRequestDTO request) {
        if (request == null || request.getBatchId() == null || request.getChunkNo() == null
                || request.getRowCount() == null || StringUtils.isBlank(request.getContentHash())
                || request.getRows() == null || request.getRows().isEmpty()
                || request.getRowCount() != request.getRows().size()) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }
    }

    private QuestionImportBatchDO requireOwnedBatch(Long batchId) {
        QuestionImportBatchDO batch = questionImportBatchDOMapper.selectByPrimaryKey(batchId);
        if (batch == null) {
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_NOT_FOUND);
        }
        Long currentUserId = questionAccessSupport.requireCurrentUserId();
        if (!currentUserId.equals(batch.getUserId())) {
            throw new BizException(ResponseCodeEnum.NO_PERMISSION);
        }
        return batch;
    }

    private void requireStatus(QuestionImportBatchDO batch, QuestionImportBatchStatusEnum expectedStatus) {
        if (!expectedStatus.getCode().equals(batch.getStatus())) {
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_STATUS_ILLEGAL);
        }
    }

    private List<QuestionImportTempDO> buildTempRows(AppendImportChunkRequestDTO request) {
        List<QuestionImportTempDO> rows = new ArrayList<>(request.getRows().size());
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < request.getRows().size(); i++) {
            ImportQuestionRowDTO row = request.getRows().get(i);
            rows.add(QuestionImportTempDO.builder()
                    .batchId(request.getBatchId())
                    .chunkNo(request.getChunkNo())
                    .rowNo(i + 1)
                    .chunkRowCount(request.getRowCount())
                    .contentHash(request.getContentHash())
                    .content(row.getContent())
                    .answer(row.getAnswer())
                    .analysis(row.getAnalysis())
                    .createdTime(now)
                    .build());
        }
        return rows;
    }

    private BizException bizException(String errorCode, String errorMessage) {
        return new BizException(new BaseExceptionInterface() {
            @Override
            public String getErrorCode() {
                return errorCode;
            }

            @Override
            public String getErrorMessage() {
                return errorMessage;
            }
        });
    }
}
