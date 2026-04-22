package com.zhoushuo.eaqb.question.bank.biz.domain.mapper;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionOutboxEventDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface QuestionOutboxEventDOMapper {
    int insert(QuestionOutboxEventDO record);

    QuestionOutboxEventDO selectByTaskId(@Param("taskId") Long taskId);

    List<QuestionOutboxEventDO> selectByEventStatus(@Param("eventStatus") String eventStatus);

    int updateEventStatus(@Param("id") Long id,
                          @Param("expectedStatus") String expectedStatus,
                          @Param("targetStatus") String targetStatus,
                          @Param("dispatchRetryCount") Integer dispatchRetryCount);
}
