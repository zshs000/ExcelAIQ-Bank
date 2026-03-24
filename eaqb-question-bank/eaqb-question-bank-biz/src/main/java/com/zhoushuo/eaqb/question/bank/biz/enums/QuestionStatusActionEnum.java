package com.zhoushuo.eaqb.question.bank.biz.enums;

/**
 * 题目状态流转动作定义。
 * 动作本身不代表最终状态，需要结合状态机计算 next status。
 */
public enum QuestionStatusActionEnum {
    /**
     * 发送题目进入 AI 处理流程。
     */
    SEND,
    /**
     * 题目已成功投递到 AI 处理链路。
     */
    SEND_SUCCESS,
    /**
     * 题目投递失败，回退到待处理状态。
     */
    SEND_FAIL,
    /**
     * AI 处理成功回调。
     */
    AI_SUCCESS,
    /**
     * AI 处理失败回调。
     */
    AI_FAIL,
    /**
     * 人工审核通过。
     */
    APPROVE,
    /**
     * 人工审核驳回。
     */
    REJECT,
    /**
     * 对失败题目进行重试。
     */
    RETRY
}
