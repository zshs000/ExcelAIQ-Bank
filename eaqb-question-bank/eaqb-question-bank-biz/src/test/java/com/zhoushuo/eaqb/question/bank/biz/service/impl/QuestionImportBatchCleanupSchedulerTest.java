package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionImportBatchDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionImportTempDOMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionImportBatchCleanupSchedulerTest {

    @Mock
    private QuestionImportBatchDOMapper questionImportBatchDOMapper;

    @Mock
    private QuestionImportTempDOMapper questionImportTempDOMapper;

    @InjectMocks
    private QuestionImportBatchCleanupScheduler scheduler;

    @Test
    void cleanupExpiredImportBatches_shouldDeleteExpiredBatchesByStatusRetention() {
        ReflectionTestUtils.setField(scheduler, "clock",
                Clock.fixed(Instant.parse("2026-04-10T10:00:00Z"), ZoneId.of("Asia/Shanghai")));
        ReflectionTestUtils.setField(scheduler, "batchLimit", 200);
        ReflectionTestUtils.setField(scheduler, "committedRetentionDays", 7);
        ReflectionTestUtils.setField(scheduler, "failedRetentionDays", 7);
        ReflectionTestUtils.setField(scheduler, "abortedRetentionDays", 1);

        LocalDateTime committedCutoff = LocalDateTime.of(2026, 4, 3, 18, 0, 0);
        LocalDateTime abortedCutoff = LocalDateTime.of(2026, 4, 9, 18, 0, 0);
        when(questionImportBatchDOMapper.selectExpiredBatchIdsByStatusAndUpdatedBefore("COMMITTED", committedCutoff, 200))
                .thenReturn(List.of(1L, 2L));
        when(questionImportBatchDOMapper.selectExpiredBatchIdsByStatusAndUpdatedBefore("FAILED", committedCutoff, 200))
                .thenReturn(List.of(3L));
        when(questionImportBatchDOMapper.selectExpiredBatchIdsByStatusAndUpdatedBefore("ABORTED", abortedCutoff, 200))
                .thenReturn(List.of(4L));

        scheduler.cleanupExpiredImportBatches();

        verify(questionImportBatchDOMapper).selectExpiredBatchIdsByStatusAndUpdatedBefore("COMMITTED", committedCutoff, 200);
        verify(questionImportBatchDOMapper).selectExpiredBatchIdsByStatusAndUpdatedBefore("FAILED", committedCutoff, 200);
        verify(questionImportBatchDOMapper).selectExpiredBatchIdsByStatusAndUpdatedBefore("ABORTED", abortedCutoff, 200);

        InOrder inOrder = inOrder(questionImportTempDOMapper, questionImportBatchDOMapper);
        inOrder.verify(questionImportTempDOMapper).deleteByBatchIds(List.of(1L, 2L));
        inOrder.verify(questionImportBatchDOMapper).deleteByIds(List.of(1L, 2L));
        inOrder.verify(questionImportTempDOMapper).deleteByBatchIds(List.of(3L));
        inOrder.verify(questionImportBatchDOMapper).deleteByIds(List.of(3L));
        inOrder.verify(questionImportTempDOMapper).deleteByBatchIds(List.of(4L));
        inOrder.verify(questionImportBatchDOMapper).deleteByIds(List.of(4L));
    }

    @Test
    void cleanupExpiredImportBatches_noExpiredBatches_shouldSkipDelete() {
        ReflectionTestUtils.setField(scheduler, "clock",
                Clock.fixed(Instant.parse("2026-04-10T10:00:00Z"), ZoneId.of("Asia/Shanghai")));
        ReflectionTestUtils.setField(scheduler, "batchLimit", 200);
        ReflectionTestUtils.setField(scheduler, "committedRetentionDays", 7);
        ReflectionTestUtils.setField(scheduler, "failedRetentionDays", 7);
        ReflectionTestUtils.setField(scheduler, "abortedRetentionDays", 1);

        when(questionImportBatchDOMapper.selectExpiredBatchIdsByStatusAndUpdatedBefore(eq("COMMITTED"), any(LocalDateTime.class), eq(200)))
                .thenReturn(List.of());
        when(questionImportBatchDOMapper.selectExpiredBatchIdsByStatusAndUpdatedBefore(eq("FAILED"), any(LocalDateTime.class), eq(200)))
                .thenReturn(List.of());
        when(questionImportBatchDOMapper.selectExpiredBatchIdsByStatusAndUpdatedBefore(eq("ABORTED"), any(LocalDateTime.class), eq(200)))
                .thenReturn(List.of());

        scheduler.cleanupExpiredImportBatches();

        verify(questionImportTempDOMapper, never()).deleteByBatchIds(any());
        verify(questionImportBatchDOMapper, never()).deleteByIds(any());
    }
}
