package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionImportBatchDOMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class QuestionImportBatchStatusWriter {

    @Resource
    private QuestionImportBatchDOMapper questionImportBatchDOMapper;

    /**
     * 批次冻结属于失败通知语义，必须独立提交，不能被外层追加事务一并回滚。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markFailed(Long batchId, String expectedStatus, String errorMessage) {
        questionImportBatchDOMapper.markFailed(batchId, expectedStatus, errorMessage);
    }
}
