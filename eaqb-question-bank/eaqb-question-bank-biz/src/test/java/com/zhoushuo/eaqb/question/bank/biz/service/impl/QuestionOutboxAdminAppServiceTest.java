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
import com.zhoushuo.framework.common.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionOutboxAdminAppServiceTest {

    @Mock
    private QuestionOutboxEventDOMapper questionOutboxEventDOMapper;

    @Mock
    private QuestionProcessTaskDOMapper questionProcessTaskDOMapper;

    @Mock
    private QuestionDOMapper questionDOMapper;

    @Mock
    private QuestionOutboxAdminRetryLogWriter questionOutboxAdminRetryLogWriter;

    @InjectMocks
    private QuestionOutboxAdminAppService questionOutboxAdminAppService;

    @Test
    void questionOutboxAdminRetryLogWriter_shouldUseRequiresNewTransaction() throws NoSuchMethodException {
        Method method = QuestionOutboxAdminRetryLogWriter.class
                .getMethod("logRetryFailure", Long.class, Long.class, Long.class, Long.class, String.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
        assertEquals(1, transactional.rollbackFor().length);
        assertEquals(Exception.class, transactional.rollbackFor()[0]);
    }

    @Test
    void listFailedOutboxEvents_shouldReturnFailedEventsWithTaskAndQuestionSnapshot() {
        LocalDateTime now = LocalDateTime.now();
        when(questionOutboxEventDOMapper.selectByEventStatus(OutboxEventStatusEnum.FAILED.getCode()))
                .thenReturn(List.of(
                        QuestionOutboxEventDO.builder()
                                .id(9001L)
                                .taskId(8001L)
                                .eventStatus(OutboxEventStatusEnum.FAILED.getCode())
                                .dispatchRetryCount(8)
                                .lastError("mq down")
                                .lastErrorTime(now.minusMinutes(1))
                                .createdTime(now.minusHours(1))
                                .updatedTime(now)
                                .build()
                ));
        when(questionProcessTaskDOMapper.selectByPrimaryKey(8001L)).thenReturn(
                QuestionProcessTaskDO.builder()
                        .id(8001L)
                        .questionId(7001L)
                        .mode("GENERATE")
                        .taskStatus(QuestionProcessTaskStatusEnum.FAILED.getCode())
                        .sourceQuestionStatus(QuestionProcessStatusEnum.WAITING.getCode())
                        .failureReason("mq down")
                        .build()
        );
        when(questionDOMapper.selectByPrimaryKey(7001L)).thenReturn(
                QuestionDO.builder()
                        .id(7001L)
                        .processStatus(QuestionProcessStatusEnum.WAITING.getCode())
                        .content("题目A")
                        .build()
        );

        Response<List<FailedOutboxEventVO>> response = questionOutboxAdminAppService.listFailedOutboxEvents();

        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertEquals(1, response.getData().size());
        FailedOutboxEventVO vo = response.getData().get(0);
        assertEquals(9001L, vo.getEventId());
        assertEquals(8001L, vo.getTaskId());
        assertEquals(7001L, vo.getQuestionId());
        assertEquals(OutboxEventStatusEnum.FAILED.getCode(), vo.getEventStatus());
        assertEquals(QuestionProcessTaskStatusEnum.FAILED.getCode(), vo.getTaskStatus());
        assertEquals(QuestionProcessStatusEnum.WAITING.getCode(), vo.getQuestionStatus());
        assertEquals("mq down", vo.getLastError());
    }

    @Test
    void retryFailedOutboxEvent_shouldRestoreQuestionTaskAndOutboxForRedispatch() {
        LocalDateTime beforeRetry = LocalDateTime.now();
        QuestionOutboxEventDO event = QuestionOutboxEventDO.builder()
                .id(9002L)
                .taskId(8002L)
                .eventStatus(OutboxEventStatusEnum.FAILED.getCode())
                .dispatchRetryCount(8)
                .lastError("mq down")
                .lastErrorTime(beforeRetry.minusMinutes(2))
                .build();
        QuestionProcessTaskDO task = QuestionProcessTaskDO.builder()
                .id(8002L)
                .questionId(7002L)
                .taskStatus(QuestionProcessTaskStatusEnum.FAILED.getCode())
                .sourceQuestionStatus(QuestionProcessStatusEnum.WAITING.getCode())
                .failureReason("mq down")
                .build();
        QuestionDO question = QuestionDO.builder()
                .id(7002L)
                .processStatus(QuestionProcessStatusEnum.WAITING.getCode())
                .build();

        when(questionOutboxEventDOMapper.selectByPrimaryKey(9002L)).thenReturn(event);
        when(questionProcessTaskDOMapper.selectByPrimaryKey(8002L)).thenReturn(task);
        when(questionDOMapper.selectByPrimaryKey(7002L)).thenReturn(question);
        when(questionDOMapper.transitStatus(7002L,
                QuestionProcessStatusEnum.WAITING.getCode(),
                QuestionProcessStatusEnum.DISPATCHING.getCode())).thenReturn(1);
        when(questionProcessTaskDOMapper.updateTaskStatus(8002L,
                QuestionProcessTaskStatusEnum.FAILED.getCode(),
                QuestionProcessTaskStatusEnum.PENDING_DISPATCH.getCode(),
                null)).thenReturn(1);
        when(questionOutboxEventDOMapper.updateAfterDispatchFailure(
                eq(9002L),
                eq(OutboxEventStatusEnum.FAILED.getCode()),
                eq(OutboxEventStatusEnum.RETRYABLE.getCode()),
                eq(8),
                any(LocalDateTime.class),
                eq("mq down"),
                eq(event.getLastErrorTime())
        )).thenReturn(1);

        Response<Void> response = questionOutboxAdminAppService.retryFailedOutboxEvent(9002L);

        assertTrue(response.isSuccess());
        verify(questionDOMapper).transitStatus(7002L,
                QuestionProcessStatusEnum.WAITING.getCode(),
                QuestionProcessStatusEnum.DISPATCHING.getCode());
        verify(questionProcessTaskDOMapper).updateTaskStatus(8002L,
                QuestionProcessTaskStatusEnum.FAILED.getCode(),
                QuestionProcessTaskStatusEnum.PENDING_DISPATCH.getCode(),
                null);
        verify(questionOutboxEventDOMapper).updateAfterDispatchFailure(
                eq(9002L),
                eq(OutboxEventStatusEnum.FAILED.getCode()),
                eq(OutboxEventStatusEnum.RETRYABLE.getCode()),
                eq(8),
                any(LocalDateTime.class),
                eq("mq down"),
                eq(event.getLastErrorTime())
        );
    }

    @Test
    void retryFailedOutboxEvent_nonFailedEvent_shouldReturnFail() {
        when(questionOutboxEventDOMapper.selectByPrimaryKey(9003L)).thenReturn(
                QuestionOutboxEventDO.builder()
                        .id(9003L)
                        .taskId(8003L)
                        .eventStatus(OutboxEventStatusEnum.RETRYABLE.getCode())
                        .build()
        );

        Response<Void> response = questionOutboxAdminAppService.retryFailedOutboxEvent(9003L);

        assertFalse(response.isSuccess());
        verify(questionProcessTaskDOMapper, never()).selectByPrimaryKey(8003L);
        verify(questionDOMapper, never()).selectByPrimaryKey(7003L);
        verify(questionOutboxAdminRetryLogWriter).logRetryFailure(
                9003L, 8003L, null, null, "仅允许重试 FAILED 状态的 outbox 事件"
        );
    }

    @Test
    void retryFailedOutboxEvent_missingEvent_shouldReturnFailAndLog() {
        when(questionOutboxEventDOMapper.selectByPrimaryKey(9006L)).thenReturn(null);

        Response<Void> response = questionOutboxAdminAppService.retryFailedOutboxEvent(9006L);

        assertFalse(response.isSuccess());
        verify(questionOutboxAdminRetryLogWriter).logRetryFailure(
                9006L, null, null, null, "失败 outbox 事件不存在"
        );
    }

    @Test
    void retryFailedOutboxEvent_questionNotInSourceStatus_shouldReturnFail() {
        when(questionOutboxEventDOMapper.selectByPrimaryKey(9004L)).thenReturn(
                QuestionOutboxEventDO.builder()
                        .id(9004L)
                        .taskId(8004L)
                        .eventStatus(OutboxEventStatusEnum.FAILED.getCode())
                        .dispatchRetryCount(8)
                        .build()
        );
        when(questionProcessTaskDOMapper.selectByPrimaryKey(8004L)).thenReturn(
                QuestionProcessTaskDO.builder()
                        .id(8004L)
                        .questionId(7004L)
                        .taskStatus(QuestionProcessTaskStatusEnum.FAILED.getCode())
                        .sourceQuestionStatus(QuestionProcessStatusEnum.WAITING.getCode())
                        .build()
        );
        when(questionDOMapper.selectByPrimaryKey(7004L)).thenReturn(
                QuestionDO.builder()
                        .id(7004L)
                        .processStatus(QuestionProcessStatusEnum.COMPLETED.getCode())
                        .build()
        );

        Response<Void> response = questionOutboxAdminAppService.retryFailedOutboxEvent(9004L);

        assertFalse(response.isSuccess());
        verify(questionDOMapper, never()).transitStatus(
                eq(7004L),
                eq(QuestionProcessStatusEnum.WAITING.getCode()),
                eq(QuestionProcessStatusEnum.DISPATCHING.getCode())
        );
        verify(questionProcessTaskDOMapper, never()).updateTaskStatus(any(), any(), any(), any());
        verify(questionOutboxEventDOMapper, never()).updateAfterDispatchFailure(any(), any(), any(), any(), any(), any(), any());
        verify(questionOutboxAdminRetryLogWriter).logRetryFailure(
                9004L, 8004L, 7004L, null, "题目当前状态为 COMPLETED，期望为 WAITING，无法人工重试"
        );
    }

    @Test
    void retryFailedOutboxEvent_taskRestoreFailed_shouldThrowToAvoidPartialCommit() {
        when(questionOutboxEventDOMapper.selectByPrimaryKey(9005L)).thenReturn(
                QuestionOutboxEventDO.builder()
                        .id(9005L)
                        .taskId(8005L)
                        .eventStatus(OutboxEventStatusEnum.FAILED.getCode())
                        .dispatchRetryCount(8)
                        .build()
        );
        when(questionProcessTaskDOMapper.selectByPrimaryKey(8005L)).thenReturn(
                QuestionProcessTaskDO.builder()
                        .id(8005L)
                        .questionId(7005L)
                        .taskStatus(QuestionProcessTaskStatusEnum.FAILED.getCode())
                        .sourceQuestionStatus(QuestionProcessStatusEnum.WAITING.getCode())
                        .build()
        );
        when(questionDOMapper.selectByPrimaryKey(7005L)).thenReturn(
                QuestionDO.builder()
                        .id(7005L)
                        .processStatus(QuestionProcessStatusEnum.WAITING.getCode())
                        .build()
        );
        when(questionDOMapper.transitStatus(7005L,
                QuestionProcessStatusEnum.WAITING.getCode(),
                QuestionProcessStatusEnum.DISPATCHING.getCode())).thenReturn(1);
        when(questionProcessTaskDOMapper.updateTaskStatus(8005L,
                QuestionProcessTaskStatusEnum.FAILED.getCode(),
                QuestionProcessTaskStatusEnum.PENDING_DISPATCH.getCode(),
                null)).thenReturn(0);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> questionOutboxAdminAppService.retryFailedOutboxEvent(9005L));

        assertTrue(ex.getMessage().contains("恢复任务到 PENDING_DISPATCH 失败"));
        verify(questionOutboxEventDOMapper, never()).updateAfterDispatchFailure(any(), any(), any(), any(), any(), any(), any());
        verify(questionOutboxAdminRetryLogWriter).logRetryFailure(
                9005L, 8005L, 7005L, null, "恢复任务到 PENDING_DISPATCH 失败，请稍后重试"
        );
    }
}
