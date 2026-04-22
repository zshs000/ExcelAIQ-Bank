package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionOutboxEventDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionProcessTaskDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionOutboxEventDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionProcessTaskDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.enums.OutboxEventStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.service.QuestionDispatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
/**
 * outbox 派发扫描器。
 *
 * <p>职责：定时扫描待发送的 outbox 事件，并把它们交给 QuestionDispatchService 执行真正派发。</p>
 * <p>这层的意义在于把“同步请求受理”与“外部 MQ 投递”解耦：</p>
 * <p>1. 请求线程只负责写 task/outbox；</p>
 * <p>2. 扫描器负责异步补发与重试；</p>
 * <p>3. 这样即使主流程提交后进程短暂异常，系统仍可从 outbox 中恢复待发送任务。</p>
 */
public class QuestionOutboxDispatchScheduler {

    private final QuestionOutboxEventDOMapper questionOutboxEventDOMapper;
    private final QuestionProcessTaskDOMapper questionProcessTaskDOMapper;
    private final QuestionDOMapper questionDOMapper;
    private final QuestionDispatchService questionDispatchService;

    public QuestionOutboxDispatchScheduler(QuestionOutboxEventDOMapper questionOutboxEventDOMapper,
                                           QuestionProcessTaskDOMapper questionProcessTaskDOMapper,
                                           QuestionDOMapper questionDOMapper,
                                           QuestionDispatchService questionDispatchService) {
        this.questionOutboxEventDOMapper = questionOutboxEventDOMapper;
        this.questionProcessTaskDOMapper = questionProcessTaskDOMapper;
        this.questionDOMapper = questionDOMapper;
        this.questionDispatchService = questionDispatchService;
    }

    /**
     * 定时扫描待派发 outbox 事件。
     *
     * <p>当前会同时扫描两类事件：</p>
     * <p>1. NEW：刚创建、尚未尝试发送；</p>
     * <p>2. RETRYABLE：上一次发送失败，允许后续补发。</p>
     *
     * <p>注意：这是一个“至少一次”风格的调度器。
     * 当前实现还没有多实例 claim/抢占机制，因此多实例部署时仍需额外保护，
     * 否则可能出现并发扫描同一批 outbox 的情况。</p>
     */
    @Scheduled(
            initialDelayString = "${question.dispatch.outbox-scan-initial-delay-ms:10000}",
            fixedDelayString = "${question.dispatch.outbox-scan-delay-ms:5000}"
    )
    public void scanPendingOutboxEvents() {
        List<QuestionOutboxEventDO> pendingEvents = new ArrayList<>();
        pendingEvents.addAll(defaultIfNull(questionOutboxEventDOMapper.selectByEventStatus(OutboxEventStatusEnum.NEW.getCode())));
        pendingEvents.addAll(defaultIfNull(questionOutboxEventDOMapper.selectByEventStatus(OutboxEventStatusEnum.RETRYABLE.getCode())));

        if (pendingEvents.isEmpty()) {
            return;
        }

        // TODO: when multiple instances of question-bank are deployed, protect this scanner with a distributed lock
        // or a stronger claiming mechanism to avoid duplicate concurrent scans of the same outbox rows.
        for (QuestionOutboxEventDO event : pendingEvents) {
            try {
                dispatchSingleEvent(event);
            } catch (Exception e) {
                log.error("outbox 扫描派发异常，eventId={}, taskId={}",
                        event == null ? null : event.getId(),
                        event == null ? null : event.getTaskId(),
                        e);
            }
        }
    }

    /**
     * 派发单条 outbox 事件。
     *
     * <p>这里会先补齐派发上下文：</p>
     * <p>1. 根据 outbox 找 task；</p>
     * <p>2. 根据 task 找题目；</p>
     * <p>3. 上下文齐全后再调用 dispatchService 执行真正派发。</p>
     *
     * <p>如果 task 或题目缺失，说明本地流程数据已经不完整，当前实现选择记录日志并跳过，
     * 不在扫描器里做激进修复。</p>
     */
    private void dispatchSingleEvent(QuestionOutboxEventDO event) {
        if (event == null || event.getTaskId() == null) {
            return;
        }
        QuestionProcessTaskDO task = questionProcessTaskDOMapper.selectByPrimaryKey(event.getTaskId());
        if (task == null || task.getQuestionId() == null) {
            log.warn("outbox 扫描跳过：未找到 task，eventId={}, taskId={}", event.getId(), event.getTaskId());
            return;
        }
        QuestionDO question = questionDOMapper.selectByPrimaryKey(task.getQuestionId());
        if (question == null) {
            log.warn("outbox 扫描跳过：未找到题目，eventId={}, taskId={}, questionId={}",
                    event.getId(), task.getId(), task.getQuestionId());
            return;
        }
        questionDispatchService.dispatchTask(task.getId(), question);
    }

    /**
     * 防御式处理 mapper 可能返回的 null，统一转成空列表，避免扫描流程里出现 NPE。
     */
    private List<QuestionOutboxEventDO> defaultIfNull(List<QuestionOutboxEventDO> events) {
        return events == null ? Collections.emptyList() : events;
    }
}