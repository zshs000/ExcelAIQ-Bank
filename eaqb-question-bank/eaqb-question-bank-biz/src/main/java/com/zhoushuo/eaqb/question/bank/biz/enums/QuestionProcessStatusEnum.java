package com.zhoushuo.eaqb.question.bank.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Optional;

/**
 * 题目快照状态定义（统一口径）。
 *
 * 这些状态描述的是题目对外可见的粗粒度阶段，不等价于完整流程真相。
 * 完整工作流还需要结合 task、validation record、outbox、inbox 等流程表一起判断。
 *
 * 快照流转主线：
 * WAITING -> DISPATCHING -> PROCESSING -> REVIEW_PENDING -> COMPLETED
 * 快照异常支线：
 * PROCESSING -> PROCESS_FAILED -> WAITING（重试）
 */
@Getter
@AllArgsConstructor
public enum QuestionProcessStatusEnum {
    /**
     * 待处理：题目当前未处于有效处理轮次，可再次发起处理。
     */
    WAITING("WAITING"),
    /**
     * 派发中：题目快照显示本轮任务已提交派发，但链路尚未确认进入处理中。
     */
    DISPATCHING("DISPATCHING"),
    /**
     * 处理中：题目快照显示当前存在进行中的 AI 处理任务。
     */
    PROCESSING("PROCESSING"),
    /**
     * 待审核：AI 已返回结果，等待人工处理。
     * 具体是生成审核还是校验审核，需结合 lastReviewMode 和流程表判断。
     */
    REVIEW_PENDING("REVIEW_PENDING"),
    /**
     * 已完成：题目当前处于已确认完成的业务阶段。
     */
    COMPLETED("COMPLETED"),
    /**
     * 处理失败：本轮 AI 链路失败；后续可重试回到 WAITING。
     */
    PROCESS_FAILED("PROCESS_FAILED");

    private final String code;

    /**
     * 将字符串状态转换为枚举，忽略大小写与前后空格。
     */
    public static Optional<QuestionProcessStatusEnum> from(String status) {
        if (StringUtils.isBlank(status)) {
            // 兼容历史数据或测试用例未显式设置状态的场景。
            return Optional.of(WAITING);
        }
        return Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(status.trim()))
                .findFirst();
    }
}
