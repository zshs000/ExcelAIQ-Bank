package com.zhoushuo.eaqb.question.bank.biz.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FailedOutboxEventVO {
    private Long eventId;

    private Long taskId;

    private Long questionId;

    private String mode;

    private String eventStatus;

    private String taskStatus;

    private String questionStatus;

    private String sourceQuestionStatus;

    private Integer dispatchRetryCount;

    private LocalDateTime nextRetryTime;

    private String lastError;

    private LocalDateTime lastErrorTime;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
