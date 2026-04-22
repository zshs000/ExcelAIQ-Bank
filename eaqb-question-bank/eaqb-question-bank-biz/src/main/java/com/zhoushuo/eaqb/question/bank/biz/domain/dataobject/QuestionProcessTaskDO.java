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
public class QuestionProcessTaskDO {
    private Long id;

    private Long questionId;

    private String mode;

    private Integer attemptNo;

    private String taskStatus;

    private String callbackKey;

    private String sourceQuestionStatus;

    private String failureReason;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
