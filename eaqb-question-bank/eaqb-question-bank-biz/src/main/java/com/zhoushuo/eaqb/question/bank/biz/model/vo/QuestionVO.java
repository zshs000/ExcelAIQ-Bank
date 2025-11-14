package com.zhoushuo.eaqb.question.bank.biz.model.vo;



import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 题目响应DTO
 */
@Builder
@Data
public class QuestionVO {
    /**
     * 题目ID
     */
    private Long id;

    /**
     * 题目内容
     */
    private String content;

    /**
     * 题目答案
     */
    private String answer;

    /**
     * 题目解析
     */
    private String analysis;

    /**
     * 处理状态
     */
    private String processStatus;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;

    /**
     * 创建人ID
     */
    private Long createdBy;
}