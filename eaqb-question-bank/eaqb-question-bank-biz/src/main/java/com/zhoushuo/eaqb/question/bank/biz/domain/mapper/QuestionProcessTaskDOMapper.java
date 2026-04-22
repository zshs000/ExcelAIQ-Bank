package com.zhoushuo.eaqb.question.bank.biz.domain.mapper;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionProcessTaskDO;
import org.apache.ibatis.annotations.Param;

public interface QuestionProcessTaskDOMapper {
    int insert(QuestionProcessTaskDO record);

    QuestionProcessTaskDO selectActiveTaskByQuestionId(@Param("questionId") Long questionId);

    QuestionProcessTaskDO selectByPrimaryKey(@Param("id") Long id);

    int updateTaskStatus(@Param("id") Long id,
                         @Param("expectedStatus") String expectedStatus,
                         @Param("targetStatus") String targetStatus,
                         @Param("failureReason") String failureReason);
}
