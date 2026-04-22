package com.zhoushuo.eaqb.question.bank.biz.state;

import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionProcessStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionStatusActionEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

@Slf4j
/**
 * 题目工作流辅助类。
 * 负责模式归一化、状态值归一化，以及对状态机结果做便于业务层使用的静态包装；
 * 不负责具体业务编排，也不承载 task / outbox / inbox / validation record 等流程语义。
 */
public final class QuestionWorkflowHelper {

    // 生成模式：题目暂无正式答案，AI 负责生成答案。
    public static final String MODE_GENERATE = "GENERATE";
    // 校验模式：题目已有正式答案，AI 负责校验并给出建议答案。
    public static final String MODE_VALIDATE = "VALIDATE";

    private QuestionWorkflowHelper() {
    }

    /**
     * 规范化外部传入的处理模式。
     * 空值按 GENERATE 处理，非法值返回 null 交给业务层决定是否拒绝。
     */
    public static String normalizeMode(String mode) {
        if (StringUtils.isBlank(mode)) {
            return MODE_GENERATE;
        }
        String requestedMode = mode.trim().toUpperCase();
        if (!MODE_GENERATE.equals(requestedMode) && !MODE_VALIDATE.equals(requestedMode)) {
            log.warn("收到非法 mode={}", requestedMode);
            return null;
        }
        return requestedMode;
    }

    /**
     * 规范化内部链路中的处理模式。
     * 与 normalizeMode 不同，空值或非法值都回退到调用方给定的 fallback。
     */
    public static String normalizeModeOrFallback(String mode, String fallback) {
        if (StringUtils.isBlank(mode)) {
            return fallback;
        }
        String normalized = normalizeMode(mode);
        return normalized == null ? fallback : normalized;
    }

    /**
     * 将数据库或消息中的状态值归一化为标准状态码。
     * 已知状态会收敛到枚举 code，未知值保持原样，避免这里直接吞数据。
     */
    public static String resolvedStatusCode(String currentStatus) {
        return QuestionProcessStatusEnum.from(currentStatus)
                .map(QuestionProcessStatusEnum::getCode)
                .orElse(currentStatus);
    }

    /**
     * 根据当前状态和动作计算下一状态。
     * 不存在合法流转时返回 null，表示当前动作在该状态下不成立。
     * 这属于确定性业务错误，调用方应按语义拒绝或跳过，而不是重试。
     */
    public static String nextStatusCodeOrNull(String currentStatus, QuestionStatusActionEnum action) {
        Optional<QuestionProcessStatusEnum> next = QuestionStatusStateMachine.next(currentStatus, action);
        return next.map(QuestionProcessStatusEnum::getCode).orElse(null);
    }
}
