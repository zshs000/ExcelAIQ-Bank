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
import com.zhoushuo.eaqb.question.bank.biz.model.vo.FailedOutboxEventVO;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class QuestionOutboxAdminAppService {

    private final QuestionOutboxEventDOMapper questionOutboxEventDOMapper;
    private final QuestionProcessTaskDOMapper questionProcessTaskDOMapper;
    private final QuestionDOMapper questionDOMapper;
    private final QuestionOutboxAdminRetryLogWriter questionOutboxAdminRetryLogWriter;

    public QuestionOutboxAdminAppService(QuestionOutboxEventDOMapper questionOutboxEventDOMapper,
                                         QuestionProcessTaskDOMapper questionProcessTaskDOMapper,
                                         QuestionDOMapper questionDOMapper,
                                         QuestionOutboxAdminRetryLogWriter questionOutboxAdminRetryLogWriter) {
        this.questionOutboxEventDOMapper = questionOutboxEventDOMapper;
        this.questionProcessTaskDOMapper = questionProcessTaskDOMapper;
        this.questionDOMapper = questionDOMapper;
        this.questionOutboxAdminRetryLogWriter = questionOutboxAdminRetryLogWriter;
    }

    public Response<List<FailedOutboxEventVO>> listFailedOutboxEvents() {
        List<QuestionOutboxEventDO> failedEvents = questionOutboxEventDOMapper.selectByEventStatus(OutboxEventStatusEnum.FAILED.getCode());
        if (failedEvents == null || failedEvents.isEmpty()) {
            return Response.success(Collections.emptyList());
        }
        List<FailedOutboxEventVO> result = failedEvents.stream()
                .map(this::toFailedOutboxEventVO)
                .collect(Collectors.toList());
        return Response.success(result);
    }

    @Transactional(rollbackFor = Exception.class)
    public Response<Void> retryFailedOutboxEvent(Long eventId) {
        if (eventId == null) {
            return failWithLog(null, null, null, "eventId 不能为空");
        }

        QuestionOutboxEventDO event = questionOutboxEventDOMapper.selectByPrimaryKey(eventId);
        if (event == null) {
            return failWithLog(eventId, null, null, "失败 outbox 事件不存在");
        }
        if (!OutboxEventStatusEnum.FAILED.getCode().equals(event.getEventStatus())) {
            return failWithLog(event.getId(), event.getTaskId(), null, "仅允许重试 FAILED 状态的 outbox 事件");
        }
        if (event.getTaskId() == null) {
            return failWithLog(event.getId(), null, null, "outbox 事件缺少 taskId，无法重试");
        }

        QuestionProcessTaskDO task = questionProcessTaskDOMapper.selectByPrimaryKey(event.getTaskId());
        if (task == null) {
            return failWithLog(event.getId(), event.getTaskId(), null, "outbox 对应任务不存在，无法重试");
        }
        if (!QuestionProcessTaskStatusEnum.FAILED.getCode().equals(task.getTaskStatus())) {
            return failWithLog(event.getId(), task.getId(), task.getQuestionId(), "仅允许重试 task=FAILED 的 outbox 事件");
        }
        if (task.getQuestionId() == null) {
            return failWithLog(event.getId(), task.getId(), null, "任务缺少 questionId，无法重试");
        }

        QuestionDO question = questionDOMapper.selectByPrimaryKey(task.getQuestionId());
        if (question == null) {
            return failWithLog(event.getId(), task.getId(), task.getQuestionId(), "outbox 对应题目不存在，无法重试");
        }

        String sourceQuestionStatus = resolveSourceQuestionStatus(task);
        String currentQuestionStatus = QuestionProcessStatusEnum.from(question.getProcessStatus())
                .map(QuestionProcessStatusEnum::getCode)
                .orElse(question.getProcessStatus());
        if (!StringUtils.equals(sourceQuestionStatus, currentQuestionStatus)) {
            return failWithLog(
                    event.getId(),
                    task.getId(),
                    question.getId(),
                    String.format("题目当前状态为 %s，期望为 %s，无法人工重试", currentQuestionStatus, sourceQuestionStatus)
            );
        }

        try {
            restoreForManualRetry(event, task, question, sourceQuestionStatus);
        } catch (RuntimeException e) {
            logRetryFailure(event, task, question, e);
            throw e;
        }

        log.info("管理员人工重试失败 outbox 成功，eventId={}, taskId={}, questionId={}",
                event.getId(), task.getId(), question.getId());
        return Response.success();
    }

    private void restoreForManualRetry(QuestionOutboxEventDO event,
                                       QuestionProcessTaskDO task,
                                       QuestionDO question,
                                       String sourceQuestionStatus) {
        int questionUpdatedRows = questionDOMapper.transitStatus(
                question.getId(),
                sourceQuestionStatus,
                QuestionProcessStatusEnum.DISPATCHING.getCode()
        );
        if (questionUpdatedRows <= 0) {
            throw new IllegalStateException("恢复题目到 DISPATCHING 失败，请稍后重试");
        }

        int taskUpdatedRows = questionProcessTaskDOMapper.updateTaskStatus(
                task.getId(),
                QuestionProcessTaskStatusEnum.FAILED.getCode(),
                QuestionProcessTaskStatusEnum.PENDING_DISPATCH.getCode(),
                null
        );
        if (taskUpdatedRows <= 0) {
            throw new IllegalStateException("恢复任务到 PENDING_DISPATCH 失败，请稍后重试");
        }

        int eventUpdatedRows = questionOutboxEventDOMapper.updateAfterDispatchFailure(
                event.getId(),
                OutboxEventStatusEnum.FAILED.getCode(),
                OutboxEventStatusEnum.RETRYABLE.getCode(),
                safeRetryCount(event),
                LocalDateTime.now(),
                event.getLastError(),
                event.getLastErrorTime()
        );
        if (eventUpdatedRows <= 0) {
            throw new IllegalStateException("恢复 outbox 到 RETRYABLE 失败，请稍后重试");
        }
    }

    private FailedOutboxEventVO toFailedOutboxEventVO(QuestionOutboxEventDO event) {
        QuestionProcessTaskDO task = event == null || event.getTaskId() == null
                ? null
                : questionProcessTaskDOMapper.selectByPrimaryKey(event.getTaskId());
        QuestionDO question = task == null || task.getQuestionId() == null
                ? null
                : questionDOMapper.selectByPrimaryKey(task.getQuestionId());
        return FailedOutboxEventVO.builder()
                .eventId(event == null ? null : event.getId())
                .taskId(event == null ? null : event.getTaskId())
                .questionId(task == null ? null : task.getQuestionId())
                .mode(task == null ? null : task.getMode())
                .eventStatus(event == null ? null : event.getEventStatus())
                .taskStatus(task == null ? null : task.getTaskStatus())
                .questionStatus(question == null ? null : question.getProcessStatus())
                .sourceQuestionStatus(task == null ? null : task.getSourceQuestionStatus())
                .dispatchRetryCount(event == null ? null : event.getDispatchRetryCount())
                .nextRetryTime(event == null ? null : event.getNextRetryTime())
                .lastError(event == null ? null : event.getLastError())
                .lastErrorTime(event == null ? null : event.getLastErrorTime())
                .createdTime(event == null ? null : event.getCreatedTime())
                .updatedTime(event == null ? null : event.getUpdatedTime())
                .build();
    }

    private String resolveSourceQuestionStatus(QuestionProcessTaskDO task) {
        return StringUtils.defaultIfBlank(
                task == null ? null : task.getSourceQuestionStatus(),
                QuestionProcessStatusEnum.WAITING.getCode()
        );
    }

    private Integer safeRetryCount(QuestionOutboxEventDO event) {
        return event == null || event.getDispatchRetryCount() == null
                ? 0
                : event.getDispatchRetryCount();
    }

    private Response<Void> failWithLog(Long eventId, Long taskId, Long questionId, String errorMessage) {
        logRetryFailure(eventId, taskId, questionId, errorMessage);
        return Response.fail(errorMessage);
    }

    private void logRetryFailure(QuestionOutboxEventDO event,
                                 QuestionProcessTaskDO task,
                                 QuestionDO question,
                                 RuntimeException e) {
        logRetryFailure(
                event == null ? null : event.getId(),
                task == null ? null : task.getId(),
                question == null ? null : question.getId(),
                e == null ? null : e.getMessage()
        );
    }

    private void logRetryFailure(Long eventId,
                                 Long taskId,
                                 Long questionId,
                                 String errorMessage) {
        try {
            questionOutboxAdminRetryLogWriter.logRetryFailure(
                    eventId,
                    taskId,
                    questionId,
                    LoginUserContextHolder.getUserId(),
                    errorMessage
            );
        } catch (Exception logException) {
            log.error("管理员人工重试失败日志写入异常，eventId={}, taskId={}, questionId={}",
                    eventId, taskId, questionId, logException);
        }
    }
}
