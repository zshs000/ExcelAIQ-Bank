package com.zhoushuo.eaqb.question.bank.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Date;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class QuestionDO {
    private Long id;
    // WAITING(待处理), DISPATCHING(派发中), PROCESSING(处理中), REVIEW_PENDING(待审核), COMPLETED(已完成), PROCESS_FAILED(处理失败)
    private String processStatus;

    // 最近一次进入 REVIEW_PENDING 的来源模式：GENERATE / VALIDATE
    private String lastReviewMode;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    private Long createdBy;

    private String content;

    private String answer;

    private String analysis;


}
