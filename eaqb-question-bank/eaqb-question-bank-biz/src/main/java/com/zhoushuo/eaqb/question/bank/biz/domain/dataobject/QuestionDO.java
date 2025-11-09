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
    //WAITING(待处理), PROCESSING(处理中), COMPLETED(已完成), FAILED(失败)
    private String processStatus;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    private Long createdBy;

    private String content;

    private String answer;

    private String analysis;


}