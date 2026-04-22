package com.zhoushuo.eaqb.question.bank.biz.model.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量发送题目到队列请求参数。
 */
@Data
public class SendToQueueRequestDTO {

    @NotEmpty(message = "题目ID列表不能为空")
    private List<Long> questionIds;

    /**
     * 支持 GENERATE / VALIDATE；为空时默认按 GENERATE 处理。
     */
    private String mode;
}
