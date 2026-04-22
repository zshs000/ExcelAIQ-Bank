package com.zhoushuo.eaqb.question.bank.biz.domain.mapper;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionValidationRecordDO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface QuestionValidationRecordDOMapper {
    int insert(QuestionValidationRecordDO record);

    QuestionValidationRecordDO selectLatestPendingByQuestionId(@Param("questionId") Long questionId);

    QuestionValidationRecordDO selectPendingByTaskId(@Param("taskId") Long taskId);

    int updateReviewOutcome(@Param("id") Long id,
                            @Param("reviewStatus") String reviewStatus,
                            @Param("reviewDecision") String reviewDecision,
                            @Param("reviewedBy") Long reviewedBy,
                            @Param("reviewedTime") LocalDateTime reviewedTime);
}
