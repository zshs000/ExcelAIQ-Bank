package com.zhoushuo.eaqb.question.bank.biz.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建题目请求DTO
 */
@Data
public class CreateQuestionDTO {
    /**
     * 题目内容
     */
    @NotNull
    private String content;

    /**
     * 题目答案
     */
    private String answer;

    /**
     * 题目解析
     */
    private String analysis;
}