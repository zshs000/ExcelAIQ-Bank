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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class QuestionOutboxDispatchScheduler {

    private final QuestionOutboxEventDOMapper questionOutboxEventDOMapper;
    private final QuestionProcessTaskDOMapper questionProcessTaskDOMapper;
    private final QuestionDOMapper questionDOMapper;
    private final QuestionDispatchService questionDispatchService;

    @Value("${question.dispatch.scan-batch-limit:100}")
    private int scanBatchLimit = 100;

    @Value("${question.dispatch.sending-timeout-ms:300000}")
    private long sendingTimeoutMs = 300000L;

    public QuestionOutboxDispatchScheduler(QuestionOutboxEventDOMapper questionOutboxEventDOMapper,
                                           QuestionProcessTaskDOMapper questionProcessTaskDOMapper,
                                           QuestionDOMapper questionDOMapper,
                                           QuestionDispatchService questionDispatchService) {
        this.questionOutboxEventDOMapper = questionOutboxEventDOMapper;
        this.questionProcessTaskDOMapper = questionProcessTaskDOMapper;
        this.questionDOMapper = questionDOMapper;
        this.questionDispatchService = questionDispatchService;
    }

    @Scheduled(
            initialDelayString = "${question.dispatch.outbox-scan-initial-delay-ms:10000}",
            fixedDelayString = "${question.dispatch.outbox-scan-delay-ms:5000}"
    )
    public void scanPendingOutboxEvents() {
        List<QuestionOutboxEventDO> pendingEvents = defaultIfNull(
                questionOutboxEventDOMapper.selectDispatchableEvents(scanBatchLimit, sendingTimeoutSeconds())
        );
        if (pendingEvents.isEmpty()) {
            return;
        }

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

    private void dispatchSingleEvent(QuestionOutboxEventDO event) {
        if (event == null || event.getId() == null || event.getTaskId() == null) {
            return;
        }
        int claimedRows = questionOutboxEventDOMapper.claimDispatchableEvent(event.getId(), sendingTimeoutSeconds());
        if (claimedRows <= 0) {
            log.info("outbox 扫描跳过：事件已被其他实例抢占或状态已变化，eventId={}, taskId={}", event.getId(), event.getTaskId());
            return;
        }
        QuestionProcessTaskDO task = questionProcessTaskDOMapper.selectByPrimaryKey(event.getTaskId());
        if (task == null || task.getQuestionId() == null) {
            log.warn("outbox 扫描跳过：未找到 task，eventId={}, taskId={}", event.getId(), event.getTaskId());
            markClaimedEventFailed(event, "outbox 对应 task 不存在或缺少 questionId");
            return;
        }
        QuestionDO question = questionDOMapper.selectByPrimaryKey(task.getQuestionId());
        if (question == null) {
            log.warn("outbox 扫描跳过：未找到题目，eventId={}, taskId={}, questionId={}",
                    event.getId(), task.getId(), task.getQuestionId());
            markClaimedEventFailed(event, "outbox 对应题目不存在");
            return;
        }
        questionDispatchService.dispatchTask(task.getId(), question);
    }

    private List<QuestionOutboxEventDO> defaultIfNull(List<QuestionOutboxEventDO> events) {
        return events == null ? Collections.emptyList() : events;
    }

    /**
     * 将 sendingTimeoutMs 转换为秒数，向上取整。
     * 非法或过小的配置（负数、0、小于 1 秒）统一按 1 秒处理。
     */
    private int sendingTimeoutSeconds() {
        long timeoutMs = Math.max(sendingTimeoutMs, 1000L);
        long seconds = (timeoutMs + 999L) / 1000L;
        return seconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds;
    }

    private void markClaimedEventFailed(QuestionOutboxEventDO event, String lastError) {
        questionOutboxEventDOMapper.updateAfterDispatchFailure(
                event.getId(),
                OutboxEventStatusEnum.SENDING.getCode(),
                OutboxEventStatusEnum.FAILED.getCode(),
                safeRetryCount(event),
                null,
                lastError,
                LocalDateTime.now()
        );
    }

    private int safeRetryCount(QuestionOutboxEventDO event) {
        return event == null || event.getDispatchRetryCount() == null ? 0 : event.getDispatchRetryCount();
    }
}
