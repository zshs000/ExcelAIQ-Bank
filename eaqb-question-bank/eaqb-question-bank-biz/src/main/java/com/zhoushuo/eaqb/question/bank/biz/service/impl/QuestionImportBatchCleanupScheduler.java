package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionImportBatchDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionImportTempDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionImportBatchStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class QuestionImportBatchCleanupScheduler {

    private static final String APPENDING_TIMEOUT_MESSAGE = "import batch timed out before finish/commit";

    private final QuestionImportBatchDOMapper questionImportBatchDOMapper;
    private final QuestionImportTempDOMapper questionImportTempDOMapper;
    private Clock clock = Clock.systemDefaultZone();

    @Value("${question.import.cleanup.batch-limit:200}")
    private int batchLimit = 200;

    @Value("${question.import.cleanup.appending-timeout-hours:6}")
    private int appendingTimeoutHours = 6;

    @Value("${question.import.cleanup.committed-retention-days:7}")
    private int committedRetentionDays = 7;

    @Value("${question.import.cleanup.failed-retention-days:7}")
    private int failedRetentionDays = 7;

    @Value("${question.import.cleanup.aborted-retention-days:1}")
    private int abortedRetentionDays = 1;

    public QuestionImportBatchCleanupScheduler(QuestionImportBatchDOMapper questionImportBatchDOMapper,
                                               QuestionImportTempDOMapper questionImportTempDOMapper) {
        this.questionImportBatchDOMapper = questionImportBatchDOMapper;
        this.questionImportTempDOMapper = questionImportTempDOMapper;
    }

    @Scheduled(
            initialDelayString = "${question.import.cleanup.initial-delay-ms:60000}",
            fixedDelayString = "${question.import.cleanup.delay-ms:3600000}"
    )
    public void cleanupExpiredImportBatches() {
        abortTimedOutAppendingBatches();
        cleanupStatus(QuestionImportBatchStatusEnum.COMMITTED.getCode(), committedRetentionDays);
        cleanupStatus(QuestionImportBatchStatusEnum.FAILED.getCode(), failedRetentionDays);
        cleanupStatus(QuestionImportBatchStatusEnum.ABORTED.getCode(), abortedRetentionDays);
    }

    private void abortTimedOutAppendingBatches() {
        try {
            LocalDateTime updatedBefore = LocalDateTime.now(clock).minusHours(appendingTimeoutHours);
            List<Long> timedOutBatchIds = defaultIfNull(
                    questionImportBatchDOMapper.selectExpiredBatchIdsByStatusAndUpdatedBefore(
                            QuestionImportBatchStatusEnum.APPENDING.getCode(), updatedBefore, batchLimit)
            );
            if (timedOutBatchIds.isEmpty()) {
                return;
            }
            questionImportBatchDOMapper.markAbortedByIds(
                    timedOutBatchIds,
                    QuestionImportBatchStatusEnum.APPENDING.getCode(),
                    APPENDING_TIMEOUT_MESSAGE
            );
            log.info("中止超时导入批次完成, count={}", timedOutBatchIds.size());
        } catch (Exception e) {
            log.error("中止超时导入批次异常", e);
        }
    }

    private void cleanupStatus(String status, int retentionDays) {
        try {
            LocalDateTime updatedBefore = LocalDateTime.now(clock).minusDays(retentionDays);
            List<Long> expiredBatchIds = defaultIfNull(
                    questionImportBatchDOMapper.selectExpiredBatchIdsByStatusAndUpdatedBefore(status, updatedBefore, batchLimit)
            );
            if (expiredBatchIds.isEmpty()) {
                return;
            }

            questionImportTempDOMapper.deleteByBatchIds(expiredBatchIds);
            questionImportBatchDOMapper.deleteByIds(expiredBatchIds);
            log.info("清理过期导入批次完成, status={}, count={}", status, expiredBatchIds.size());
        } catch (Exception e) {
            log.error("清理过期导入批次异常, status={}", status, e);
            // TODO: when multiple instances of question-bank are deployed, protect this cleanup scanner
            // with a distributed lock or row-claiming strategy to avoid duplicate concurrent cleanup.
        }
    }

    private List<Long> defaultIfNull(List<Long> ids) {
        return ids == null ? Collections.emptyList() : ids;
    }
}
