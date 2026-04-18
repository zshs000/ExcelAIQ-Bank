package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionImportBatchDOMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 导入批次状态写入器。
 * 负责承接需要独立提交的失败态写入，避免失败原因被外层事务回滚后丢失。
 */
@Component
public class QuestionImportBatchStatusWriter {

    @Resource
    private QuestionImportBatchDOMapper questionImportBatchDOMapper;

    /**
     * 将批次冻结为 FAILED。
     * 这里使用 REQUIRES_NEW，是为了保证即使外层 append/commit 事务随后回滚，
     * 本次失败原因也已经单独落库，方便后续排障和状态追踪。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markFailed(Long batchId, String expectedStatus, String errorMessage) {
        questionImportBatchDOMapper.markFailed(batchId, expectedStatus, errorMessage);
    }
}
