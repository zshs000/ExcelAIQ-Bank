package com.zhoushuo.eaqb.question.bank.biz.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QuestionPageQueryDTO {
    private int page;
    private int pageSize;


    /**
     * id
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

}
