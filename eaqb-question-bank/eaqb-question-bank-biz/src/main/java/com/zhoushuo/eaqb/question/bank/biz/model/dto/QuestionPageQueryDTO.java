package com.zhoushuo.eaqb.question.bank.biz.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QuestionPageQueryDTO {
    @Min(value = 1, message = "页码最小为1")
    private int page;

    @Min(value = 1, message = "每页数量最小为1")
    @Max(value = 200, message = "每页数量不能超过200")
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
