package com.zhoushuo.eaqb.question.bank.biz.service.impl.imports;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionImportBatchDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionImportTempDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionImportBatchStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.question.bank.resp.CommitImportBatchResponseDTO;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;

import java.util.List;

/**
 * 导入批次提交执行器。
 * 在事务内负责“临时行 -> 正式题目”的提交动作及批次状态推进。
 */
public class ImportBatchCommitExecutor {

    private final QuestionDOMapper questionDOMapper;
    private final ImportBatchStateMachine importBatchStateMachine;
    private final ImportBatchAssembler importBatchAssembler;

    public ImportBatchCommitExecutor(QuestionDOMapper questionDOMapper,
                                     ImportBatchStateMachine importBatchStateMachine,
                                     ImportBatchAssembler importBatchAssembler) {
        this.questionDOMapper = questionDOMapper;
        this.importBatchStateMachine = importBatchStateMachine;
        this.importBatchAssembler = importBatchAssembler;
    }

    /**
     * 提交步骤：
     * 1. 将 tempRows 映射为正式题目；
     * 2. 批量写入正式题目表；
     * 3. 批次状态更新为 COMMITTED；
     * 4. 返回提交结果。
     */
    public Response<CommitImportBatchResponseDTO> commit(QuestionImportBatchDO batch,
                                                         List<QuestionImportTempDO> tempRows,
                                                         List<Long> questionIds) {
        List<QuestionDO> questions = importBatchAssembler.toQuestions(tempRows, questionIds, batch.getUserId());
        if (questionDOMapper.batchInsert(questions) != questions.size()) {
            throw new BizException(ResponseCodeEnum.QUESTION_IMPORT_COMMIT_FAILED);
        }
        importBatchStateMachine.markCommittedOrThrow(batch.getId(), questions.size());
        return Response.success(CommitImportBatchResponseDTO.builder()
                .batchId(batch.getId())
                .status(QuestionImportBatchStatusEnum.COMMITTED.getCode())
                .importedCount(questions.size())
                .build());
    }
}
