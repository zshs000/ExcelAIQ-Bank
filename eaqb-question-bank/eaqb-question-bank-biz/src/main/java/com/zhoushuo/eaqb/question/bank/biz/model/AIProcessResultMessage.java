package com.zhoushuo.eaqb.question.bank.biz.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * AI处理结果消息模型，用于接收AI服务处理后的结果
 */
@Data
public class AIProcessResultMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("question_id")
    private String questionId;     // 题目ID
    
    @JsonProperty("success_flag")
    private Integer successFlag;   // 成功标志位：1-成功，0-失败
    
    @JsonProperty("error_message")
    private String errorMessage;   // 错误信息，失败时非空
    
    @JsonProperty("answer")
    private String answer;         // 正确答案，成功时非空
    
    // 全参构造方法
    public AIProcessResultMessage(String questionId, Integer successFlag, String errorMessage, String answer) {
        this.questionId = questionId;
        this.successFlag = successFlag;
        this.errorMessage = errorMessage;
        this.answer = answer;
    }
    
    // 无参构造方法（反序列化需要）
    public AIProcessResultMessage() {
    }
}