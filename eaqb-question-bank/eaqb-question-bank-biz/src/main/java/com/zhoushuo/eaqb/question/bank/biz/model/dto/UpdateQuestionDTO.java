package com.zhoushuo.eaqb.question.bank.biz.model.dto;

import jakarta.validation.constraints.AssertTrue;
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

    @AssertTrue(message = "至少需要更新一个字段")
    public boolean hasAnyUpdatableField() {
        return isNotBlank(content) || isNotBlank(answer) || isNotBlank(analysis);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
