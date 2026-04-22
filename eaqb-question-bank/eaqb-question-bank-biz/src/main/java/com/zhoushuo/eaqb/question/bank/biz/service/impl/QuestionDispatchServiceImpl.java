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
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.zhoushuo.eaqb.question.bank.biz.constant.MQConstants.TOPIC_TEST;

@Slf4j
@Service
/**
 * 题目可靠派发服务实现。
 * 负责把“准备发往 AI 的题目”转换成可追踪的任务与 outbox 事件，
 * 并在真正投递 MQ 时同步推进 outbox、task、question 三类状态。
 */
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 为一次题目派发做落库准备。
     * 核心动作是：
     * 1. 抢占题目派发状态，避免重复派发；
     * 2. 创建 process task，记录这次派发的任务快照；
     * 3. 创建 outbox event，把“待发送消息”持久化下来，交给后续调度器发送。
     */
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
                .createdTime(now)
                .updatedTime(now)
                .build();
        questionOutboxEventDOMapper.insert(event);
        return taskId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 执行单个派发任务，把消息真正投递到 MQ。
     * 发送成功后会依次把 outbox 标记为 SENT、task 标记为 DISPATCHED、
     * question 状态从 DISPATCHING 推进到 PROCESSING；
     * 如果发送失败，则把 outbox 标记为 RETRYABLE，交给后续重试。
     */
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
                    outboxEvent.getDispatchRetryCount()
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
            questionOutboxEventDOMapper.updateEventStatus(
                    outboxEvent.getId(),
                    currentOutboxStatus,
                    OutboxEventStatusEnum.RETRYABLE.getCode(),
                    outboxEvent.getDispatchRetryCount() + 1
            );
            return false;
        }
    }
}
