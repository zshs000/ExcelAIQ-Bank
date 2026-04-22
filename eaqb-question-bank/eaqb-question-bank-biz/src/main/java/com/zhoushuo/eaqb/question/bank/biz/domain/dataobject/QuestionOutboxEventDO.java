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
public class QuestionOutboxEventDO {
    private Long id;

    private Long taskId;

    private String eventStatus;

    private Integer dispatchRetryCount;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
