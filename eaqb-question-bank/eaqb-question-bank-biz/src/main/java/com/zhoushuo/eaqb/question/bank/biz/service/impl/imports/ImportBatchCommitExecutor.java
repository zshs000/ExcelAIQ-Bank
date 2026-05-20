package com.zhoushuo.eaqb.question.bank.biz.service.impl.imports;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionImportBatchDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionImportBatchStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.question.bank.resp.CommitImportBatchResponseDTO;
import com.zhoushuo.framework.common.exception.BizException;
import com.zhoushuo.framework.common.response.Response;
import org.springframework.stereotype.Component;

/**
 * 导入批次提交执行器。
 * 在事务内负责“临时行 -> 正式题目”的提交动作及批次状态推进。
 */
@Component
public class ImportBatchCommitExecutor {

    private final QuestionDOMapper questionDOMapper;
    private final ImportBatchStateMachine importBatchStateMachine;

    public ImportBatchCommitExecutor(QuestionDOMapper questionDOMapper,
                                     ImportBatchStateMachine importBatchStateMachine) {
        this.questionDOMapper = questionDOMapper;
        this.importBatchStateMachine = importBatchStateMachine;
    }

    /**
     * 提交步骤：
     * 1. 通过 INSERT INTO ... SELECT ... 将临时行转正；
     * 2. 校验导入数量；
     * 3. 批次状态更新为 COMMITTED；
     * 4. 返回提交结果。
     */
    public Response<CommitImportBatchResponseDTO> commit(QuestionImportBatchDO batch) {
        int importedCount = questionDOMapper.insertFromImportTemp(batch.getId(), batch.getUserId());
        if (importedCount != batch.getTotalRowCount()) {
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_COMMIT_FAILED);
        }
        importBatchStateMachine.markCommittedOrThrow(batch.getId(), importedCount);
        return Response.success(CommitImportBatchResponseDTO.builder()
                .batchId(batch.getId())
                .status(QuestionImportBatchStatusEnum.COMMITTED.getCode())
                .importedCount(importedCount)
                .build());
    }
}
