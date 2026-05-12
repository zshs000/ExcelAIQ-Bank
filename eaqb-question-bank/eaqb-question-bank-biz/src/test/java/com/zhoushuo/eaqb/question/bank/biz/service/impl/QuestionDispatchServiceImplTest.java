package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionOutboxEventDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionProcessTaskDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionOutboxEventDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionProcessTaskDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.model.QuestionMessage;
import com.zhoushuo.eaqb.question.bank.biz.rpc.DistributedIdGeneratorRpcService;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionDispatchServiceImplTest {

    @Mock
    private QuestionDOMapper questionDOMapper;

    @Mock
    private QuestionProcessTaskDOMapper questionProcessTaskDOMapper;

    @Mock
    private QuestionOutboxEventDOMapper questionOutboxEventDOMapper;

    @Mock
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @InjectMocks
    private QuestionDispatchServiceImpl questionDispatchService;

    @Test
    void prepareQuestionDispatch_shouldCreateTaskAndOutbox() {
        QuestionDO question = QuestionDO.builder()
                .id(1001L)
                .content("什么是事务")
                .answer(null)
                .processStatus("WAITING")
                .createdBy(123L)
                .build();

        when(questionDOMapper.transitStatus(1001L, "WAITING", "DISPATCHING")).thenReturn(1);
        when(questionProcessTaskDOMapper.selectActiveTaskByQuestionId(1001L)).thenReturn(null);
        when(distributedIdGeneratorRpcService.nextQuestionBankEntityId()).thenReturn("8001", "9001");
        when(questionProcessTaskDOMapper.insert(any(QuestionProcessTaskDO.class))).thenReturn(1);
        when(questionOutboxEventDOMapper.insert(any(QuestionOutboxEventDO.class))).thenReturn(1);

        Long taskId = questionDispatchService.prepareQuestionDispatch(question, "GENERATE");

        assertEquals(8001L, taskId);

        ArgumentCaptor<QuestionProcessTaskDO> taskCaptor = ArgumentCaptor.forClass(QuestionProcessTaskDO.class);
        verify(questionProcessTaskDOMapper).insert(taskCaptor.capture());
        assertEquals(8001L, taskCaptor.getValue().getId());
        assertEquals(1001L, taskCaptor.getValue().getQuestionId());
        assertEquals("GENERATE", taskCaptor.getValue().getMode());
        assertEquals(1, taskCaptor.getValue().getAttemptNo());
        assertEquals("PENDING_DISPATCH", taskCaptor.getValue().getTaskStatus());

        ArgumentCaptor<QuestionOutboxEventDO> eventCaptor = ArgumentCaptor.forClass(QuestionOutboxEventDO.class);
        verify(questionOutboxEventDOMapper).insert(eventCaptor.capture());
        assertEquals(9001L, eventCaptor.getValue().getId());
        assertEquals(8001L, eventCaptor.getValue().getTaskId());
        assertEquals("NEW", eventCaptor.getValue().getEventStatus());
        assertEquals(0, eventCaptor.getValue().getDispatchRetryCount());
        assertNull(eventCaptor.getValue().getNextRetryTime());
        assertNull(eventCaptor.getValue().getLastError());
        assertNull(eventCaptor.getValue().getLastErrorTime());
    }

    @Test
    void prepareQuestionDispatch_validateFromCompleted_shouldCreateTaskAndOutbox() {
        QuestionDO question = QuestionDO.builder()
                .id(1006L)
                .content("什么是回表")
                .answer("根据索引回主表取字段")
                .processStatus("COMPLETED")
                .createdBy(123L)
                .build();

        when(questionDOMapper.transitStatus(1006L, "COMPLETED", "DISPATCHING")).thenReturn(1);
        when(questionProcessTaskDOMapper.selectActiveTaskByQuestionId(1006L)).thenReturn(null);
        when(distributedIdGeneratorRpcService.nextQuestionBankEntityId()).thenReturn("8006", "9006");
        when(questionProcessTaskDOMapper.insert(any(QuestionProcessTaskDO.class))).thenReturn(1);
        when(questionOutboxEventDOMapper.insert(any(QuestionOutboxEventDO.class))).thenReturn(1);

        Long taskId = questionDispatchService.prepareQuestionDispatch(question, "VALIDATE");

        assertEquals(8006L, taskId);

        ArgumentCaptor<QuestionProcessTaskDO> taskCaptor = ArgumentCaptor.forClass(QuestionProcessTaskDO.class);
        verify(questionProcessTaskDOMapper).insert(taskCaptor.capture());
        assertEquals("COMPLETED", taskCaptor.getValue().getSourceQuestionStatus());
        verify(questionDOMapper).transitStatus(1006L, "COMPLETED", "DISPATCHING");
    }

    @Test
    void dispatchTask_shouldSendMqAndAdvanceOutboxTaskAndQuestion() {
        QuestionDO question = QuestionDO.builder()
                .id(1002L)
                .content("什么是索引")
                .answer("B+Tree")
                .processStatus("DISPATCHING")
                .build();
        when(questionProcessTaskDOMapper.selectByPrimaryKey(8002L)).thenReturn(
                QuestionProcessTaskDO.builder().id(8002L).questionId(1002L).mode("VALIDATE")
                        .attemptNo(1).taskStatus("PENDING_DISPATCH").build()
        );
        when(questionOutboxEventDOMapper.selectByTaskId(8002L)).thenReturn(
                QuestionOutboxEventDO.builder().id(9002L).taskId(8002L).eventStatus("SENDING").dispatchRetryCount(0).build()
        );
        when(questionOutboxEventDOMapper.updateEventStatus(9002L, "SENDING", "SENT", 0)).thenReturn(1);
        when(questionProcessTaskDOMapper.updateTaskStatus(8002L, "PENDING_DISPATCH", "DISPATCHED", null)).thenReturn(1);
        when(questionDOMapper.transitStatus(1002L, "DISPATCHING", "PROCESSING")).thenReturn(1);

        boolean dispatched = questionDispatchService.dispatchTask(8002L, question);

        assertTrue(dispatched);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<QuestionMessage>> captor = ArgumentCaptor.forClass((Class) Message.class);
        verify(rocketMQTemplate).syncSend(eq("TestTopic"), captor.capture());
        assertEquals("8002", captor.getValue().getPayload().getTaskId());
        assertEquals("VALIDATE", captor.getValue().getPayload().getMode());
        assertEquals("B+Tree", captor.getValue().getPayload().getCurrentAnswer());
        assertEquals("8002", captor.getValue().getHeaders().get(RocketMQHeaders.KEYS));
        verify(questionOutboxEventDOMapper).updateEventStatus(9002L, "SENDING", "SENT", 0);
        verify(questionProcessTaskDOMapper).updateTaskStatus(8002L, "PENDING_DISPATCH", "DISPATCHED", null);
        verify(questionDOMapper).transitStatus(1002L, "DISPATCHING", "PROCESSING");
    }

    @Test
    void dispatchTask_outboxNotClaimed_shouldSkipMqSend() {
        QuestionDO question = QuestionDO.builder()
                .id(1010L)
                .content("什么是二级索引")
                .processStatus("DISPATCHING")
                .build();
        when(questionProcessTaskDOMapper.selectByPrimaryKey(8010L)).thenReturn(
                QuestionProcessTaskDO.builder().id(8010L).questionId(1010L).mode("GENERATE")
                        .attemptNo(1).taskStatus("PENDING_DISPATCH").build()
        );
        when(questionOutboxEventDOMapper.selectByTaskId(8010L)).thenReturn(
                QuestionOutboxEventDO.builder().id(9010L).taskId(8010L).eventStatus("NEW").dispatchRetryCount(0).build()
        );

        boolean dispatched = questionDispatchService.dispatchTask(8010L, question);

        assertFalse(dispatched);
        verify(rocketMQTemplate, never()).syncSend(eq("TestTopic"), any(Message.class));
        verify(questionOutboxEventDOMapper, never()).updateEventStatus(
                eq(9010L), any(String.class), any(String.class), any(Integer.class));
    }

    @Test
    void dispatchTask_sendFailedBeforeMaxRetry_shouldMarkOutboxRetryableWithBackoffAndError() {
        QuestionDO question = QuestionDO.builder()
                .id(1003L)
                .content("什么是锁")
                .processStatus("DISPATCHING")
                .build();
        when(questionProcessTaskDOMapper.selectByPrimaryKey(8003L)).thenReturn(
                QuestionProcessTaskDO.builder().id(8003L).questionId(1003L).mode("GENERATE")
                        .attemptNo(1).taskStatus("PENDING_DISPATCH").sourceQuestionStatus("WAITING").build()
        );
        when(questionOutboxEventDOMapper.selectByTaskId(8003L)).thenReturn(
                QuestionOutboxEventDO.builder().id(9003L).taskId(8003L).eventStatus("SENDING").dispatchRetryCount(0).build()
        );
        when(rocketMQTemplate.syncSend(eq("TestTopic"), any(Message.class))).thenThrow(new RuntimeException("mq down"));
        when(questionOutboxEventDOMapper.updateAfterDispatchFailure(
                eq(9003L), eq("SENDING"), eq("RETRYABLE"), eq(1),
                any(LocalDateTime.class), eq("RuntimeException: mq down"), any(LocalDateTime.class)
        )).thenReturn(1);

        boolean dispatched = questionDispatchService.dispatchTask(8003L, question);

        assertFalse(dispatched);
        verify(questionOutboxEventDOMapper).updateAfterDispatchFailure(
                eq(9003L), eq("SENDING"), eq("RETRYABLE"), eq(1),
                any(LocalDateTime.class), eq("RuntimeException: mq down"), any(LocalDateTime.class)
        );
    }

    @Test
    void dispatchTask_sendFailedAtMaxRetry_shouldMarkOutboxFailedAndRollbackTaskAndQuestion() {
        QuestionDO question = QuestionDO.builder()
                .id(1007L)
                .content("什么是回表")
                .processStatus("DISPATCHING")
                .build();
        when(questionProcessTaskDOMapper.selectByPrimaryKey(8007L)).thenReturn(
                QuestionProcessTaskDO.builder().id(8007L).questionId(1007L).mode("VALIDATE")
                        .attemptNo(1).taskStatus("PENDING_DISPATCH").sourceQuestionStatus("COMPLETED").build()
        );
        when(questionOutboxEventDOMapper.selectByTaskId(8007L)).thenReturn(
                QuestionOutboxEventDO.builder().id(9007L).taskId(8007L).eventStatus("SENDING").dispatchRetryCount(7).build()
        );
        when(rocketMQTemplate.syncSend(eq("TestTopic"), any(Message.class))).thenThrow(new RuntimeException("mq down"));
        when(questionOutboxEventDOMapper.updateAfterDispatchFailure(
                eq(9007L), eq("SENDING"), eq("FAILED"), eq(8),
                isNull(), eq("RuntimeException: mq down"), any(LocalDateTime.class)
        )).thenReturn(1);
        when(questionProcessTaskDOMapper.updateTaskStatus(8007L, "PENDING_DISPATCH", "FAILED", "RuntimeException: mq down")).thenReturn(1);
        when(questionDOMapper.transitStatus(1007L, "DISPATCHING", "COMPLETED")).thenReturn(1);

        boolean dispatched = questionDispatchService.dispatchTask(8007L, question);

        assertFalse(dispatched);
        verify(questionOutboxEventDOMapper).updateAfterDispatchFailure(
                eq(9007L), eq("SENDING"), eq("FAILED"), eq(8),
                isNull(), eq("RuntimeException: mq down"), any(LocalDateTime.class)
        );
        verify(questionProcessTaskDOMapper).updateTaskStatus(8007L, "PENDING_DISPATCH", "FAILED", "RuntimeException: mq down");
        verify(questionDOMapper).transitStatus(1007L, "DISPATCHING", "COMPLETED");
    }

    @Test
    void dispatchTask_stateSyncFailedAfterSend_shouldReturnFalseAndMarkRetryable() {
        QuestionDO question = QuestionDO.builder()
                .id(1004L)
                .content("什么是事务隔离级别")
                .processStatus("DISPATCHING")
                .build();
        when(questionProcessTaskDOMapper.selectByPrimaryKey(8004L)).thenReturn(
                QuestionProcessTaskDO.builder().id(8004L).questionId(1004L).mode("GENERATE")
                        .attemptNo(1).taskStatus("PENDING_DISPATCH").build()
        );
        when(questionOutboxEventDOMapper.selectByTaskId(8004L)).thenReturn(
                QuestionOutboxEventDO.builder().id(9004L).taskId(8004L).eventStatus("SENDING").dispatchRetryCount(0).build()
        );
        when(questionOutboxEventDOMapper.updateEventStatus(9004L, "SENDING", "SENT", 0)).thenReturn(0);
        when(questionOutboxEventDOMapper.updateAfterDispatchFailure(
                eq(9004L), eq("SENDING"), eq("RETRYABLE"), eq(1),
                any(LocalDateTime.class), eq("IllegalStateException: outbox 状态推进失败，taskId=8004"), any(LocalDateTime.class)
        )).thenReturn(1);

        boolean dispatched = questionDispatchService.dispatchTask(8004L, question);

        assertFalse(dispatched);
        verify(questionOutboxEventDOMapper).updateEventStatus(9004L, "SENDING", "SENT", 0);
        verify(questionOutboxEventDOMapper).updateAfterDispatchFailure(
                eq(9004L), eq("SENDING"), eq("RETRYABLE"), eq(1),
                any(LocalDateTime.class), eq("IllegalStateException: outbox 状态推进失败，taskId=8004"), any(LocalDateTime.class)
        );
    }

    @Test
    void dispatchTask_questionStatusSyncFailedAfterBrokerAck_shouldReturnTrueAndNotMarkRetryable() {
        QuestionDO question = QuestionDO.builder()
                .id(1005L)
                .content("什么是MVCC")
                .processStatus("DISPATCHING")
                .build();
        when(questionProcessTaskDOMapper.selectByPrimaryKey(8005L)).thenReturn(
                QuestionProcessTaskDO.builder().id(8005L).questionId(1005L).mode("GENERATE")
                        .attemptNo(1).taskStatus("PENDING_DISPATCH").build()
        );
        when(questionOutboxEventDOMapper.selectByTaskId(8005L)).thenReturn(
                QuestionOutboxEventDO.builder().id(9005L).taskId(8005L).eventStatus("SENDING").dispatchRetryCount(0).build()
        );
        when(questionOutboxEventDOMapper.updateEventStatus(9005L, "SENDING", "SENT", 0)).thenReturn(1);
        when(questionProcessTaskDOMapper.updateTaskStatus(8005L, "PENDING_DISPATCH", "DISPATCHED", null)).thenReturn(1);
        when(questionDOMapper.transitStatus(1005L, "DISPATCHING", "PROCESSING")).thenReturn(0);

        boolean dispatched = questionDispatchService.dispatchTask(8005L, question);

        assertTrue(dispatched);
        verify(questionOutboxEventDOMapper).updateEventStatus(9005L, "SENDING", "SENT", 0);
        verify(questionProcessTaskDOMapper).updateTaskStatus(8005L, "PENDING_DISPATCH", "DISPATCHED", null);
        verify(questionDOMapper).transitStatus(1005L, "DISPATCHING", "PROCESSING");
        verify(questionOutboxEventDOMapper, never()).updateAfterDispatchFailure(
                eq(9005L), any(String.class), any(String.class), any(Integer.class),
                any(LocalDateTime.class), any(String.class), any(LocalDateTime.class)
        );
    }
}
