package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionOutboxAdminRetryLogDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionOutboxAdminRetryLogDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.rpc.DistributedIdGeneratorRpcService;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class QuestionOutboxAdminRetryLogWriter {

    private final QuestionOutboxAdminRetryLogDOMapper questionOutboxAdminRetryLogDOMapper;
    private final DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;

    public QuestionOutboxAdminRetryLogWriter(QuestionOutboxAdminRetryLogDOMapper questionOutboxAdminRetryLogDOMapper,
                                             DistributedIdGeneratorRpcService distributedIdGeneratorRpcService) {
        this.questionOutboxAdminRetryLogDOMapper = questionOutboxAdminRetryLogDOMapper;
        this.distributedIdGeneratorRpcService = distributedIdGeneratorRpcService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void logRetryFailure(Long eventId, Long taskId, Long questionId, Long adminUserId, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        Long resolvedAdminUserId = adminUserId != null ? adminUserId : LoginUserContextHolder.getUserId();
        QuestionOutboxAdminRetryLogDO record = QuestionOutboxAdminRetryLogDO.builder()
                .id(Long.valueOf(distributedIdGeneratorRpcService.nextQuestionBankEntityId()))
                .eventId(eventId)
                .taskId(taskId)
                .questionId(questionId)
                .adminUserId(resolvedAdminUserId)
                .errorMessage(StringUtils.abbreviate(StringUtils.defaultIfBlank(errorMessage, "Unknown admin retry failure"), 1000))
                .createdTime(now)
                .updatedTime(now)
                .build();
        questionOutboxAdminRetryLogDOMapper.insert(record);
    }
}
