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
public class QuestionCallbackInboxDO {
    private Long id;

    private String callbackKey;

    private Long taskId;

    private String consumeStatus;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
