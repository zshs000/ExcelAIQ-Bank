package com.zhoushuo.eaqb.question.bank.biz.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新题目请求DTO
 */
@Data
public class UpdateQuestionDTO implements Serializable {

    /**
     * 题目内容
     */
    private String content;

    /**
     * 答案
     */
    private String answer;

    /**
     * 解析
     */
    private String analysis;
}