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
public class QuestionValidationRecordDO {
    private Long id;

    private Long questionId;

    private Long taskId;

    private String originalAnswerSnapshot;

    private String aiSuggestedAnswer;

    private String validationResult;

    private String reason;

    private String reviewStatus;

    private String reviewDecision;

    private Long reviewedBy;

    private LocalDateTime reviewedTime;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
