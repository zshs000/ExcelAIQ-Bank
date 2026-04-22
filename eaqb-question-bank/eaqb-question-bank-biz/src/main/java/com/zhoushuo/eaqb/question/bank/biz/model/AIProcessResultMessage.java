package com.zhoushuo.eaqb.question.bank.biz.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;

/**
 * AI 处理结果消息模型（统一格式，兼容旧字段）。
 */
@Data
public class AIProcessResultMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("task_id")
    private String taskId;         // 回包对应的异步任务ID

    @JsonProperty("attempt_no")
    private Integer attemptNo;     // 对应业务尝试次数

    @JsonProperty("callback_key")
    private String callbackKey;    // 端到端稳定回包幂等键

    @JsonProperty("question_id")
    private String questionId;     // 题目ID

    @JsonProperty("mode")
    private String mode;           // 兼容字段，当前系统按 GENERATE 处理
    
    @JsonProperty("success_flag")
    private Integer successFlag;   // 调用AI是否成功：1-成功，0-失败
    
    @JsonProperty("ai_answer")
    private String aiAnswer;       // AI返回答案（生成或建议答案）

    @JsonProperty("validation_result")
    private String validationResult; // 校验结论：PASS / FAIL / UNCERTAIN / NA

    @JsonProperty("reason")
    private String reason;         // 失败原因或补充说明
    
    // -------- 兼容旧字段（旧消息仍可被解析）--------
    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("answer")
    private String answer;
    
    // 全参构造方法（新格式）
    public AIProcessResultMessage(String questionId, String mode, Integer successFlag,
                                  String aiAnswer, String validationResult, String reason) {
        this.questionId = questionId;
        this.mode = mode;
        this.successFlag = successFlag;
        this.aiAnswer = aiAnswer;
        this.validationResult = validationResult;
        this.reason = reason;
    }
    
    // 无参构造方法（反序列化需要）
    public AIProcessResultMessage() {
    }

    /**
     * 兼容读取答案：优先新字段 aiAnswer，其次旧字段 answer。
     */
    public String resolvedAiAnswer() {
        return StringUtils.defaultIfBlank(aiAnswer, answer);
    }

    /**
     * 兼容读取原因：优先新字段 reason，其次旧字段 errorMessage。
     */
    public String resolvedReason() {
        return StringUtils.defaultIfBlank(reason, errorMessage);
    }

    /**
     * 兼容读取 taskId：后续回包处理应优先按 taskId 识别任务。
     */
    public String resolvedTaskId() {
        return StringUtils.trimToNull(taskId);
    }

    /**
     * attemptNo 未显式传递时，默认按第一次业务尝试处理。
     */
    public int resolvedAttemptNo() {
        return attemptNo == null || attemptNo <= 0 ? 1 : attemptNo;
    }

    /**
     * callbackKey 需要端到端稳定；未回传时退化为 taskId 作为临时兼容键。
     */
    public String resolvedCallbackKey() {
        return StringUtils.defaultIfBlank(StringUtils.trimToNull(callbackKey), resolvedTaskId());
    }
}
