package com.zhoushuo.eaqb.question.bank.biz.model.vo;

import lombok.Builder;
import lombok.Data;

/**
 * sendQuestionsToQueue 接口的结构化返回结果。
 *
 * <p>注意：这个对象表达的是“本次请求的受理摘要”，不是“MQ 实际发送摘要”。</p>
 * <p>在真实 outbox 链路下，接口返回时消息通常还没有真正发出，因此某些字段需要结合场景理解。</p>
 */
@Data
@Builder
public class SendToQueueResultVO {
    /**
     * 本次处理模式：GENERATE / VALIDATE。
     */
    private String mode;

    /**
     * 调用方请求的题目 ID 总数。
     */
    private int requestedCount;

    /**
     * 数据库中实际查到的题目数。
     */
    private int foundCount;

    /**
     * 成功进入本轮处理链路的题目数。
     *
     * <p>在真实 outbox 链路下，这里的含义接近“成功受理数”：
     * 题目已完成校验并成功写入 task/outbox，后续会由扫描器异步派发。</p>
     */
    private int eligibleCount;

    /**
     * 已发送成功数。
     *
     * <p>当前字段主要兼容 mock 路径语义：</p>
     * <p>1. mock 路径下，表示本地同步模拟处理成功数；</p>
     * <p>2. 真实 outbox 链路下，接口返回时尚未真正发 MQ，因此这里固定为 0。</p>
     */
    private int sentCount;

    /**
     * 被跳过的题目总数。
     */
    private int skippedCount;

    /**
     * 因已有答案而被跳过的题目数。
     * 主要用于 GENERATE 模式。
     */
    private int skippedHasAnswerCount;

    /**
     * 因缺少答案而被跳过的题目数。
     * 主要用于 VALIDATE 模式。
     */
    private int skippedNoAnswerCount;

    /**
     * 给调用方直接展示的摘要提示。
     */
    private String message;
}