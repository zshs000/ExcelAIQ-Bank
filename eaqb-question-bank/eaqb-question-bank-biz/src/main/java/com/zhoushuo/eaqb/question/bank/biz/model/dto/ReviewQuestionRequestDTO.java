package com.zhoushuo.eaqb.question.bank.biz.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 审核题目请求参数。
 */
@Data
public class ReviewQuestionRequestDTO {

    /**
     * 审核决策：
     * GENERATE: APPLY_AI / REJECT
     * VALIDATE: KEEP_ORIGINAL / APPLY_AI / REJECT
     */
    @NotBlank(message = "审核决策不能为空")
    private String decision;

    /**
     * 兼容旧字段，后续可移除。
     */
    private String action;
}
