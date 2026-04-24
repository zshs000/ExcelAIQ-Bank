package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionOutboxEventDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionProcessTaskDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionOutboxEventDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionProcessTaskDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.enums.OutboxEventStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionProcessStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionProcessTaskStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionStatusActionEnum;
import com.zhoushuo.eaqb.question.bank.biz.model.QuestionMessage;
import com.zhoushuo.eaqb.question.bank.biz.rpc.DistributedIdGeneratorRpcService;
import com.zhoushuo.eaqb.question.bank.biz.service.QuestionDispatchService;
import com.zhoushuo.eaqb.question.bank.biz.state.QuestionStatusStateMachine;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

import static com.zhoushuo.eaqb.question.bank.biz.constant.MQConstants.TOPIC_TEST;

@Slf4j
@Service
public class QuestionDispatchServiceImpl implements QuestionDispatchService {

    @Autowired
    private QuestionDOMapper questionDOMapper;

    @Autowired
    private QuestionProcessTaskDOMapper questionProcessTaskDOMapper;

    @Autowired
    private QuestionOutboxEventDOMapper questionOutboxEventDOMapper;

    @Autowired
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    @Value("${question.dispatch.max-retries:8}")
    private int maxRetries = 8;

    @Value("${question.dispatch.retry-initial-delay-ms:30000}")
    private long retryInitialDelayMs = 30000L;

    @Value("${question.dispatch.retry-max-delay-ms:1800000}")
    private long retryMaxDelayMs = 1800000L;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long prepareQuestionDispatch(QuestionDO question, String mode) {
        if (question == null || question.getId() == null || StringUtils.isBlank(mode)) {
            return null;
        }
        if (questionProcessTaskDOMapper.selectActiveTaskByQuestionId(question.getId()) != null) {
            return null;
        }
        String currentStatus = question.getProcessStatus();
        String nextStatus = QuestionStatusStateMachine.next(currentStatus, QuestionStatusActionEnum.SEND)
                .map(it -> it.getCode())
                .orElse(null);
        if (nextStatus == null) {
            return null;
        }
        if (questionDOMapper.transitStatus(question.getId(), currentStatus, nextStatus) <= 0) {
            return null;
        }

        Long taskId = Long.valueOf(distributedIdGeneratorRpcService.nextQuestionBankEntityId());
        Long eventId = Long.valueOf(distributedIdGeneratorRpcService.nextQuestionBankEntityId());
        LocalDateTime now = LocalDateTime.now();

        QuestionProcessTaskDO task = QuestionProcessTaskDO.builder()
                .id(taskId)
                .questionId(question.getId())
                .mode(mode)
                .attemptNo(1)
                .taskStatus(QuestionProcessTaskStatusEnum.PENDING_DISPATCH.getCode())
                .callbackKey(String.valueOf(taskId))
                .sourceQuestionStatus(currentStatus)
                .createdTime(now)
                .updatedTime(now)
                .build();
        questionProcessTaskDOMapper.insert(task);

        QuestionOutboxEventDO event = QuestionOutboxEventDO.builder()
                .id(eventId)
                .taskId(taskId)
                .eventStatus(OutboxEventStatusEnum.NEW.getCode())
                .dispatchRetryCount(0)
                .nextRetryTime(null)
                .lastError(null)
                .lastErrorTime(null)
                .createdTime(now)
                .updatedTime(now)
                .build();
        questionOutboxEventDOMapper.insert(event);
        return taskId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean dispatchTask(Long taskId, QuestionDO question) {
        if (taskId == null || question == null || question.getId() == null || rocketMQTemplate == null) {
            return false;
        }
        QuestionProcessTaskDO task = questionProcessTaskDOMapper.selectByPrimaryKey(taskId);
        QuestionOutboxEventDO outboxEvent = questionOutboxEventDOMapper.selectByTaskId(taskId);
        if (task == null || outboxEvent == null) {
            return false;
        }

        String currentOutboxStatus = outboxEvent.getEventStatus();
        boolean brokerAcked = false;
        boolean outboxMarkedSent = false;
        try {
            QuestionMessage payload = new QuestionMessage(
                    String.valueOf(task.getId()),
                    task.getAttemptNo(),
                    String.valueOf(question.getId()),
                    question.getContent(),
                    task.getMode(),
                    "VALIDATE".equals(task.getMode()) ? question.getAnswer() : null
            );
            Message<QuestionMessage> message = MessageBuilder.withPayload(payload)
                    .setHeader(RocketMQHeaders.KEYS, String.valueOf(task.getId()))
                    .build();
            rocketMQTemplate.syncSend(TOPIC_TEST, message);
            brokerAcked = true;

            int outboxUpdatedRows = questionOutboxEventDOMapper.updateEventStatus(
                    outboxEvent.getId(),
                    currentOutboxStatus,
                    OutboxEventStatusEnum.SENT.getCode(),
                    safeRetryCount(outboxEvent)
            );
            if (outboxUpdatedRows <= 0) {
                throw new IllegalStateException("outbox 状态推进失败，taskId=" + taskId);
            }
            currentOutboxStatus = OutboxEventStatusEnum.SENT.getCode();
            outboxMarkedSent = true;

            int taskUpdatedRows = questionProcessTaskDOMapper.updateTaskStatus(
                    task.getId(),
                    task.getTaskStatus(),
                    QuestionProcessTaskStatusEnum.DISPATCHED.getCode(),
                    null
            );
            if (taskUpdatedRows <= 0) {
                log.warn("MQ 已发送且 outbox 已标记 SENT，但 task 状态推进失败，保留已发送结果，taskId={}", taskId);
                return true;
            }

            String nextQuestionStatus = QuestionStatusStateMachine
                    .next(QuestionProcessStatusEnum.DISPATCHING.getCode(), QuestionStatusActionEnum.SEND_SUCCESS)
                    .map(it -> it.getCode())
                    .orElseThrow(() -> new IllegalStateException("状态机配置错误：DISPATCHING 无法通过 SEND_SUCCESS 流转"));
            int questionUpdatedRows = questionDOMapper.transitStatus(
                    question.getId(),
                    QuestionProcessStatusEnum.DISPATCHING.getCode(),
                    nextQuestionStatus
            );
            if (questionUpdatedRows <= 0) {
                log.warn("MQ 已发送且 task 已标记 DISPATCHED，但题目状态推进失败，保留已发送结果，questionId={}, taskId={}",
                        question.getId(), taskId);
                return true;
            }
            return true;
        } catch (Exception e) {
            if (brokerAcked && outboxMarkedSent) {
                log.error("MQ 已发送成功，但本地镜像同步异常；不再标记 RETRYABLE，taskId={}", taskId, e);
                return true;
            }
            markOutboxFailure(task, question, outboxEvent, currentOutboxStatus, e);
            return false;
        }
    }

    private void markOutboxFailure(QuestionProcessTaskDO task,
                                   QuestionDO question,
                                   QuestionOutboxEventDO outboxEvent,
                                   String currentOutboxStatus,
                                   Exception e) {
        int nextRetryCount = safeRetryCount(outboxEvent) + 1;
        LocalDateTime now = LocalDateTime.now();
        String lastError = buildLastError(e);
        if (nextRetryCount >= maxRetries) {
            questionOutboxEventDOMapper.updateAfterDispatchFailure(
                    outboxEvent.getId(),
                    currentOutboxStatus,
                    OutboxEventStatusEnum.FAILED.getCode(),
                    nextRetryCount,
                    null,
                    lastError,
                    now
            );
            updateTaskAndQuestionAfterFinalFailure(task, question, lastError);
            return;
        }

        LocalDateTime nextRetryTime = calculateNextRetryTime(nextRetryCount, now);
        questionOutboxEventDOMapper.updateAfterDispatchFailure(
                outboxEvent.getId(),
                currentOutboxStatus,
                OutboxEventStatusEnum.RETRYABLE.getCode(),
                nextRetryCount,
                nextRetryTime,
                lastError,
                now
        );
    }

    private void updateTaskAndQuestionAfterFinalFailure(QuestionProcessTaskDO task, QuestionDO question, String lastError) {
        int taskUpdatedRows = questionProcessTaskDOMapper.updateTaskStatus(
                task.getId(),
                task.getTaskStatus(),
                QuestionProcessTaskStatusEnum.FAILED.getCode(),
                lastError
        );
        if (taskUpdatedRows <= 0) {
            log.warn("outbox 达到最大重试次数，但 task 标记 FAILED 失败，taskId={}", task.getId());
        }

        String rollbackQuestionStatus = resolveRollbackQuestionStatus(task);
        int questionUpdatedRows = questionDOMapper.transitStatus(
                question.getId(),
                QuestionProcessStatusEnum.DISPATCHING.getCode(),
                rollbackQuestionStatus
        );
        if (questionUpdatedRows <= 0) {
            log.warn("outbox 达到最大重试次数，但题目状态回滚失败，questionId={}, rollbackStatus={}",
                    question.getId(), rollbackQuestionStatus);
        }
    }

    private String resolveRollbackQuestionStatus(QuestionProcessTaskDO task) {
        if (task != null && StringUtils.isNotBlank(task.getSourceQuestionStatus())) {
            return task.getSourceQuestionStatus();
        }
        return QuestionProcessStatusEnum.WAITING.getCode();
    }

    private String buildLastError(Exception e) {
        if (e == null) {
            return "Unknown dispatch error";
        }
        String message = StringUtils.isBlank(e.getMessage())
                ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + e.getMessage();
        return StringUtils.abbreviate(message, 1000);
    }

    private int safeRetryCount(QuestionOutboxEventDO outboxEvent) {
        return outboxEvent == null || outboxEvent.getDispatchRetryCount() == null
                ? 0
                : outboxEvent.getDispatchRetryCount();
    }

    private LocalDateTime calculateNextRetryTime(int retryCount, LocalDateTime now) {
        long delayMs = Math.max(retryInitialDelayMs, 0L);
        long maxDelay = Math.max(retryMaxDelayMs, delayMs);
        for (int i = 1; i < retryCount && delayMs < maxDelay; i++) {
            if (delayMs > maxDelay / 2) {
                delayMs = maxDelay;
                break;
            }
            delayMs = delayMs * 2;
        }
        delayMs = Math.min(delayMs, maxDelay);
        return now.plus(Duration.ofMillis(delayMs));
    }
}
