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

    @JsonProperty("task_id")
    private String taskId;         // 本次异步任务ID

    @JsonProperty("attempt_no")
    private Integer attemptNo;     // 第几次业务处理尝试

    @JsonProperty("question_id")
    private String questionId;     // 题目ID
    
    @JsonProperty("question_text")
    private String questionText;   // 题目内容

    @JsonProperty("mode")
    private String mode;           // GENERATE / VALIDATE

    @JsonProperty("current_answer")
    private String currentAnswer;  // VALIDATE 场景传原答案，GENERATE 场景可为空
    
    // 全参构造方法（可选，@Data不自动生成）
    public QuestionMessage(String taskId, Integer attemptNo, String questionId,
                           String questionText, String mode, String currentAnswer) {
        this.taskId = taskId;
        this.attemptNo = attemptNo;
        this.questionId = questionId;
        this.questionText = questionText;
        this.mode = mode;
        this.currentAnswer = currentAnswer;
    }
    
    // 无参构造方法（反序列化需要）
    public QuestionMessage() {
    }
}
