package com.zhoushuo.eaqb.question.bank.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 题目DTO,传输对象
 */
@Data
public class QuestionDTO {

    /**
     * 题目内容
     */
    @NotBlank(message = "题目内容不能为空")
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