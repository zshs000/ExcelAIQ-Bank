package com.zhoushuo.eaqb.question.bank.biz.service.impl.imports;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionImportBatchDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionImportBatchDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionImportBatchStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.question.bank.biz.service.impl.QuestionImportBatchStatusWriter;
import com.zhoushuo.framework.commono.exception.BizException;
import org.springframework.stereotype.Component;

/**
 * 导入批次状态机写操作封装。
 * 统一处理状态校验与状态流转落库，避免编排层散落 mapper 更新细节。
 */
@Component
public class ImportBatchStateMachine {

    private final QuestionImportBatchDOMapper questionImportBatchDOMapper;
    private final QuestionImportBatchStatusWriter questionImportBatchStatusWriter;

    public ImportBatchStateMachine(QuestionImportBatchDOMapper questionImportBatchDOMapper,
                                   QuestionImportBatchStatusWriter questionImportBatchStatusWriter) {
        this.questionImportBatchDOMapper = questionImportBatchDOMapper;
        this.questionImportBatchStatusWriter = questionImportBatchStatusWriter;
    }

    /**
     * 校验批次当前状态是否符合预期，不符合则抛状态非法。
     */
    public void requireStatus(QuestionImportBatchDO batch, QuestionImportBatchStatusEnum expectedStatus) {
        if (!expectedStatus.getCode().equals(batch.getStatus())) {
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_STATUS_ILLEGAL);
        }
    }

    /**
     * 通过独立事务 writer 标记失败，避免失败原因被外层事务回滚吞掉。
     */
    public void markFailedByWriter(Long batchId, QuestionImportBatchStatusEnum expectedStatus, String errorMessage) {
        questionImportBatchStatusWriter.markFailed(batchId, expectedStatus.getCode(), errorMessage);
    }

    /**
     * 直接通过 mapper 标记失败（参与当前事务）。
     */
    public void markFailedByMapper(Long batchId, QuestionImportBatchStatusEnum expectedStatus, String errorMessage) {
        questionImportBatchDOMapper.markFailed(batchId, expectedStatus.getCode(), errorMessage);
    }

    /**
     * 将批次从 APPENDING 流转到 READY，不满足条件时抛状态非法。
     */
    public void markReadyOrThrow(Long batchId, int expectedChunkCount, int expectedRowCount) {
        if (questionImportBatchDOMapper.markReady(batchId,
                QuestionImportBatchStatusEnum.APPENDING.getCode(),
                expectedChunkCount,
                expectedRowCount) <= 0) {
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_STATUS_ILLEGAL);
        }
    }

    /**
     * 将批次从 READY 流转到 COMMITTED，不满足条件时抛状态非法。
     */
    public void markCommittedOrThrow(Long batchId, int importedCount) {
        if (questionImportBatchDOMapper.markCommitted(batchId,
                QuestionImportBatchStatusEnum.READY.getCode(),
                importedCount) <= 0) {
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_BATCH_STATUS_ILLEGAL);
        }
    }
}
