package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionOutboxEventDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionProcessTaskDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionOutboxEventDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionProcessTaskDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.enums.OutboxEventStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.service.QuestionDispatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionOutboxDispatchSchedulerTest {

    @Mock
    private QuestionOutboxEventDOMapper questionOutboxEventDOMapper;

    @Mock
    private QuestionProcessTaskDOMapper questionProcessTaskDOMapper;

    @Mock
    private QuestionDOMapper questionDOMapper;

    @Mock
    private QuestionDispatchService questionDispatchService;

    @InjectMocks
    private QuestionOutboxDispatchScheduler scheduler;

    @Test
    void scanPendingOutboxEvents_shouldDispatchDueEvents() {
        when(questionOutboxEventDOMapper.selectDispatchableEvents(eq(100), eq(300))).thenReturn(List.of(
                QuestionOutboxEventDO.builder().id(9001L).taskId(8001L).eventStatus("NEW").build(),
                QuestionOutboxEventDO.builder().id(9002L).taskId(8002L).eventStatus("RETRYABLE").build()
        ));
        when(questionOutboxEventDOMapper.claimDispatchableEvent(eq(9001L), eq(300))).thenReturn(1);
        when(questionOutboxEventDOMapper.claimDispatchableEvent(eq(9002L), eq(300))).thenReturn(1);
        when(questionProcessTaskDOMapper.selectByPrimaryKey(8001L)).thenReturn(
                QuestionProcessTaskDO.builder().id(8001L).questionId(1001L).build()
        );
        when(questionProcessTaskDOMapper.selectByPrimaryKey(8002L)).thenReturn(
                QuestionProcessTaskDO.builder().id(8002L).questionId(1002L).build()
        );
        when(questionDOMapper.selectByPrimaryKey(1001L)).thenReturn(
                QuestionDO.builder().id(1001L).content("题目1").build()
        );
        when(questionDOMapper.selectByPrimaryKey(1002L)).thenReturn(
                QuestionDO.builder().id(1002L).content("题目2").build()
        );

        scheduler.scanPendingOutboxEvents();

        verify(questionOutboxEventDOMapper).selectDispatchableEvents(eq(100), eq(300));
        verify(questionOutboxEventDOMapper).claimDispatchableEvent(eq(9001L), eq(300));
        verify(questionOutboxEventDOMapper).claimDispatchableEvent(eq(9002L), eq(300));
        verify(questionDispatchService).dispatchTask(8001L, QuestionDO.builder().id(1001L).content("题目1").build());
        verify(questionDispatchService).dispatchTask(8002L, QuestionDO.builder().id(1002L).content("题目2").build());
    }

    @Test
    void scanPendingOutboxEvents_claimFailed_shouldSkipDispatch() {
        when(questionOutboxEventDOMapper.selectDispatchableEvents(eq(100), eq(300))).thenReturn(List.of(
                QuestionOutboxEventDO.builder().id(9003L).taskId(8003L).eventStatus("NEW").build()
        ));
        when(questionOutboxEventDOMapper.claimDispatchableEvent(eq(9003L), eq(300))).thenReturn(0);

        scheduler.scanPendingOutboxEvents();

        verify(questionProcessTaskDOMapper, never()).selectByPrimaryKey(8003L);
        verify(questionDispatchService, never()).dispatchTask(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void scanPendingOutboxEvents_missingTaskOrQuestion_shouldMarkClaimedEventFailed() {
        when(questionOutboxEventDOMapper.selectDispatchableEvents(eq(100), eq(300))).thenReturn(List.of(
                QuestionOutboxEventDO.builder().id(9004L).taskId(8004L).eventStatus("NEW").dispatchRetryCount(2).build(),
                QuestionOutboxEventDO.builder().id(9005L).taskId(8005L).eventStatus("RETRYABLE").dispatchRetryCount(3).build()
        ));
        when(questionOutboxEventDOMapper.claimDispatchableEvent(eq(9004L), eq(300))).thenReturn(1);
        when(questionOutboxEventDOMapper.claimDispatchableEvent(eq(9005L), eq(300))).thenReturn(1);
        when(questionProcessTaskDOMapper.selectByPrimaryKey(8004L)).thenReturn(null);
        when(questionProcessTaskDOMapper.selectByPrimaryKey(8005L)).thenReturn(
                QuestionProcessTaskDO.builder().id(8005L).questionId(1005L).build()
        );
        when(questionDOMapper.selectByPrimaryKey(1005L)).thenReturn(null);

        scheduler.scanPendingOutboxEvents();

        verify(questionOutboxEventDOMapper).updateAfterDispatchFailure(
                eq(9004L),
                eq(OutboxEventStatusEnum.SENDING.getCode()),
                eq(OutboxEventStatusEnum.FAILED.getCode()),
                eq(2),
                isNull(),
                eq("outbox 对应 task 不存在或缺少 questionId"),
                any(LocalDateTime.class)
        );
        verify(questionOutboxEventDOMapper).updateAfterDispatchFailure(
                eq(9005L),
                eq(OutboxEventStatusEnum.SENDING.getCode()),
                eq(OutboxEventStatusEnum.FAILED.getCode()),
                eq(3),
                isNull(),
                eq("outbox 对应题目不存在"),
                any(LocalDateTime.class)
        );
        verify(questionDispatchService, never()).dispatchTask(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void scanPendingOutboxEvents_dispatchThrows_shouldContinueNextEvent() {
        when(questionOutboxEventDOMapper.selectDispatchableEvents(eq(100), eq(300))).thenReturn(List.of(
                QuestionOutboxEventDO.builder().id(9005L).taskId(8005L).eventStatus("NEW").build(),
                QuestionOutboxEventDO.builder().id(9006L).taskId(8006L).eventStatus("RETRYABLE").build()
        ));
        when(questionOutboxEventDOMapper.claimDispatchableEvent(eq(9005L), eq(300))).thenReturn(1);
        when(questionOutboxEventDOMapper.claimDispatchableEvent(eq(9006L), eq(300))).thenReturn(1);
        when(questionProcessTaskDOMapper.selectByPrimaryKey(8005L)).thenReturn(
                QuestionProcessTaskDO.builder().id(8005L).questionId(1005L).build()
        );
        when(questionProcessTaskDOMapper.selectByPrimaryKey(8006L)).thenReturn(
                QuestionProcessTaskDO.builder().id(8006L).questionId(1006L).build()
        );
        QuestionDO question1 = QuestionDO.builder().id(1005L).content("题目5").build();
        QuestionDO question2 = QuestionDO.builder().id(1006L).content("题目6").build();
        when(questionDOMapper.selectByPrimaryKey(1005L)).thenReturn(question1);
        when(questionDOMapper.selectByPrimaryKey(1006L)).thenReturn(question2);
        when(questionDispatchService.dispatchTask(8005L, question1)).thenThrow(new IllegalStateException("dispatch failed"));

        scheduler.scanPendingOutboxEvents();

        verify(questionDispatchService).dispatchTask(8005L, question1);
        verify(questionDispatchService).dispatchTask(8006L, question2);
        verify(questionDispatchService, times(2)).dispatchTask(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }
}
