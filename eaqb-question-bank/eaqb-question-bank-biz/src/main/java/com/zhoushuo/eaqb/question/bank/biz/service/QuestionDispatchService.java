package com.zhoushuo.eaqb.question.bank.biz.service;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionDO;

/**
 * 题目可靠派发服务。
 *
 * <p>职责边界：</p>
 * <p>1. 承接应用层已经筛选完成、且允许进入 AI 链路的题目；</p>
 * <p>2. 把题目转换成可追踪的异步任务（task）与发送账本（outbox）；</p>
 * <p>3. 在扫描器触发时，执行真正的 MQ 投递。</p>
 *
 * <p>这里刻意把“准备派发”和“真正派发”拆成两个方法，
 * 对应 outbox 模式下的两个阶段：先落本地事实，再异步投递外部消息。</p>
 */
public interface QuestionDispatchService {

    /**
     * 为一次题目派发创建任务快照，并完成派发前的本地落库准备。
     *
     * <p>成功时返回 taskId，表示该题目已经成功进入异步派发链路：</p>
     * <p>1. 题目状态已推进到 DISPATCHING；</p>
     * <p>2. task 已创建；</p>
     * <p>3. outbox 事件已写入。</p>
     *
     * <p>注意：这里不直接发 MQ。返回 taskId 只代表“准备成功”，不代表“消息已发送成功”。</p>
     */
    Long prepareQuestionDispatch(QuestionDO question, String mode);

    /**
     * 执行单个派发任务，把题目真正投递到异步处理链路。
     *
     * <p>这个方法通常由 outbox 扫描器调用，而不是由同步请求线程直接调用。</p>
     * <p>返回 true 表示本轮调度不需要继续立即补发；返回 false 表示本轮派发失败，
     * outbox 会保持或推进到 RETRYABLE，等待后续扫描重试。</p>
     */
    boolean dispatchTask(Long taskId, QuestionDO question);
}