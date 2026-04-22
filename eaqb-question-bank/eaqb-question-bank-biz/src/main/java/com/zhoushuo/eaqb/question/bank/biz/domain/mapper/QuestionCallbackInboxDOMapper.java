package com.zhoushuo.eaqb.question.bank.biz.domain.mapper;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionCallbackInboxDO;
import org.apache.ibatis.annotations.Param;

public interface QuestionCallbackInboxDOMapper {
    int insert(QuestionCallbackInboxDO record);

    QuestionCallbackInboxDO selectByCallbackKey(@Param("callbackKey") String callbackKey);

    int updateConsumeStatus(@Param("callbackKey") String callbackKey,
                            @Param("expectedStatus") String expectedStatus,
                            @Param("targetStatus") String targetStatus);
}
