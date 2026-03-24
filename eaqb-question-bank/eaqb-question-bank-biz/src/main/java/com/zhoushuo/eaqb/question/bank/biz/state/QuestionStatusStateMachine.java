package com.zhoushuo.eaqb.question.bank.biz.state;

import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionProcessStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionStatusActionEnum;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * 轻量状态机：维护题目状态合法流转。
 * 只负责“当前状态 + 动作 -> 下一状态”判定，不负责数据库读写。
 */
public final class QuestionStatusStateMachine {

    private static final Map<QuestionProcessStatusEnum, Map<QuestionStatusActionEnum, QuestionProcessStatusEnum>> TRANSITIONS =
            new EnumMap<>(QuestionProcessStatusEnum.class);

    static {
        // 待处理题目开始发送后，进入派发中。
        add(QuestionProcessStatusEnum.WAITING, QuestionStatusActionEnum.SEND, QuestionProcessStatusEnum.DISPATCHING);
        // 派发中的题目确认发送成功后，进入处理中。
        add(QuestionProcessStatusEnum.DISPATCHING, QuestionStatusActionEnum.SEND_SUCCESS, QuestionProcessStatusEnum.PROCESSING);
        // 派发中的题目发送失败后，回退到待处理。
        add(QuestionProcessStatusEnum.DISPATCHING, QuestionStatusActionEnum.SEND_FAIL, QuestionProcessStatusEnum.WAITING);
        // 处理中的题目，AI 成功后进入待审核。
        add(QuestionProcessStatusEnum.PROCESSING, QuestionStatusActionEnum.AI_SUCCESS, QuestionProcessStatusEnum.REVIEW_PENDING);
        // 处理中的题目，AI 失败后进入处理失败。
        add(QuestionProcessStatusEnum.PROCESSING, QuestionStatusActionEnum.AI_FAIL, QuestionProcessStatusEnum.PROCESS_FAILED);
        // 待审核题目，人工通过后进入已完成。
        add(QuestionProcessStatusEnum.REVIEW_PENDING, QuestionStatusActionEnum.APPROVE, QuestionProcessStatusEnum.COMPLETED);
        // 待审核题目，人工驳回后回到待处理（可再次发送）。
        add(QuestionProcessStatusEnum.REVIEW_PENDING, QuestionStatusActionEnum.REJECT, QuestionProcessStatusEnum.WAITING);
        // 处理失败题目，可重试回到待处理。
        add(QuestionProcessStatusEnum.PROCESS_FAILED, QuestionStatusActionEnum.RETRY, QuestionProcessStatusEnum.WAITING);
    }

    private QuestionStatusStateMachine() {
    }

    /**
     * 判断某动作在当前状态下是否允许执行。
     */
    public static boolean canTransit(String currentStatus, QuestionStatusActionEnum action) {
        return next(currentStatus, action).isPresent();
    }

    /**
     * 计算下一状态，不存在合法流转时返回 empty。
     */
    public static Optional<QuestionProcessStatusEnum> next(String currentStatus, QuestionStatusActionEnum action) {
        Optional<QuestionProcessStatusEnum> current = QuestionProcessStatusEnum.from(currentStatus);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        Map<QuestionStatusActionEnum, QuestionProcessStatusEnum> actionMap = TRANSITIONS.get(current.get());
        if (actionMap == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(actionMap.get(action));
    }

    /**
     * 注册单条流转规则：from + action -> to。
     */
    private static void add(QuestionProcessStatusEnum from, QuestionStatusActionEnum action, QuestionProcessStatusEnum to) {
        TRANSITIONS.computeIfAbsent(from, k -> new EnumMap<>(QuestionStatusActionEnum.class)).put(action, to);
    }
}
