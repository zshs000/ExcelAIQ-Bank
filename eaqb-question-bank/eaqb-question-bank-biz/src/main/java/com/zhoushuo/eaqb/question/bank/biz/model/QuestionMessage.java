package com.zhoushuo.eaqb.question.bank.biz.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 题目消息模型，用于发送到消息队列
 */
@Data
public class QuestionMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("question_id")
    private String questionId;     // 题目ID
    
    @JsonProperty("question_text")
    private String questionText;   // 题目内容
    
    // 全参构造方法（可选，@Data不自动生成）
    public QuestionMessage(String questionId, String questionText) {
        this.questionId = questionId;
        this.questionText = questionText;
    }
    
    // 无参构造方法（反序列化需要）
    public QuestionMessage() {
    }
}