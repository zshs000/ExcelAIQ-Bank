package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionOutboxEventDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionProcessTaskDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionOutboxEventDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionProcessTaskDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.service.QuestionDispatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
                questionOutboxEventDOMapper.selectDispatchableEvents(scanBatchLimit)
        );
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

    private List<QuestionOutboxEventDO> defaultIfNull(List<QuestionOutboxEventDO> events) {
        return events == null ? Collections.emptyList() : events;
    }
}
