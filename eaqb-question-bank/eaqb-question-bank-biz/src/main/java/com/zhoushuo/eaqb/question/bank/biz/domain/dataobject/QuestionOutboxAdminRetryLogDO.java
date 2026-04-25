package com.zhoushuo.eaqb.question.bank.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionOutboxAdminRetryLogDO {
    private Long id;

    private Long eventId;

    private Long taskId;

    private Long questionId;

    private Long adminUserId;

    private String errorMessage;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
