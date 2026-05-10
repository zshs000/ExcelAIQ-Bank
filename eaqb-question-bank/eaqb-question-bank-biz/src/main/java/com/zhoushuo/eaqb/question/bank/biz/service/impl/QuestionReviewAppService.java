package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionProcessTaskDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionValidationRecordDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionProcessTaskDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionValidationRecordDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionProcessStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionStatusActionEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.ReviewQuestionRequestDTO;
import com.zhoushuo.eaqb.question.bank.biz.state.QuestionWorkflowHelper;
import com.zhoushuo.framework.common.exception.BizException;
import com.zhoushuo.framework.common.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
public class QuestionReviewAppService {

    private static final String DECISION_APPLY_AI = "APPLY_AI";
    private static final String DECISION_KEEP_ORIGINAL = "KEEP_ORIGINAL";
    private static final String DECISION_REJECT = "REJECT";
    private static final String REVIEW_STATUS_REVIEWED = "REVIEWED";
    private static final String REVIEW_STATUS_DISCARDED = "DISCARDED";

    @Autowired
    private QuestionDOMapper questionDOMapper;

    @Autowired
    private QuestionValidationRecordDOMapper questionValidationRecordDOMapper;

    @Autowired
    private QuestionProcessTaskDOMapper questionProcessTaskDOMapper;

    @Autowired
    private QuestionAccessSupport questionAccessSupport;

    @Transactional(rollbackFor = Exception.class)
    public Response<?> reviewQuestion(Long id, ReviewQuestionRequestDTO request) {
        log.info("开始审核题目，题目ID: {}, 请求参数: {}", id, request);
        if (id == null || request == null) {
            return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), "审核参数错误");
        }

        String normalizedDecision = normalizeReviewDecision(request);
        if (normalizedDecision == null) {
            return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(),
                    "decision 仅支持 APPLY_AI、KEEP_ORIGINAL 或 REJECT");
        }

        Long currentUserId = questionAccessSupport.requireCurrentUserId();
        QuestionDO current = questionDOMapper.selectByPrimaryKey(id);
        if (current == null) {
            throw new BizException(ResponseCodeEnum.QUESTION_NOT_FOUND);
        }
        if (!currentUserId.equals(current.getCreatedBy())) {
            throw new BizException(ResponseCodeEnum.NO_PERMISSION);
        }

        String currentStatus = QuestionWorkflowHelper.resolvedStatusCode(current.getProcessStatus());
        String reviewMode = QuestionWorkflowHelper.normalizeModeOrFallback(
                current.getLastReviewMode(), QuestionWorkflowHelper.MODE_GENERATE);
        String nextStatus = QuestionWorkflowHelper.nextStatusCodeOrNull(currentStatus,
                DECISION_REJECT.equals(normalizedDecision) ? QuestionStatusActionEnum.REJECT : QuestionStatusActionEnum.APPROVE);
        if (nextStatus == null) {
            return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(),
                    "当前状态不允许执行 " + normalizedDecision);
        }

        int updatedRows = QuestionWorkflowHelper.MODE_VALIDATE.equals(reviewMode)
                ? reviewValidatedQuestion(current, currentUserId, currentStatus, nextStatus, normalizedDecision)
                : reviewGeneratedQuestion(current, currentStatus, nextStatus, normalizedDecision);

        if (updatedRows < 0) {
            return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(),
                    reviewMode + " 模式下不允许执行 " + normalizedDecision);
        }
        if (updatedRows <= 0) {
            return Response.fail(ResponseCodeEnum.QUESTION_UPDATE_FAILED.getErrorCode(),
                    "题目状态已变化，请刷新后重试");
        }
        return Response.success();
    }

    private int reviewGeneratedQuestion(QuestionDO current, String currentStatus, String nextStatus, String decision) {
        if (DECISION_APPLY_AI.equals(decision)) {
            return questionDOMapper.transitStatus(current.getId(), currentStatus, nextStatus);
        }
        if (DECISION_REJECT.equals(decision)) {
            return questionDOMapper.transitStatusAndClearAnswerByExpectedStatus(current.getId(), currentStatus, nextStatus);
        }
        return -1;
    }

    private int reviewValidatedQuestion(QuestionDO current, Long currentUserId, String currentStatus,
                                        String nextStatus, String decision) {
        QuestionValidationRecordDO record = resolvePendingValidationRecord(current);
        if (record == null) {
            log.warn("校验审核失败，未找到待审核校验记录，questionId={}", current.getId());
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        String validatedTargetStatus = resolveValidatedTargetStatus(record, nextStatus);
        int updatedRows;
        String reviewStatus;
        if (DECISION_KEEP_ORIGINAL.equals(decision)) {
            updatedRows = questionDOMapper.transitStatus(current.getId(), currentStatus, validatedTargetStatus);
            reviewStatus = REVIEW_STATUS_REVIEWED;
        } else if (DECISION_APPLY_AI.equals(decision)) {
            updatedRows = questionDOMapper.transitStatusAndAnswer(
                    current.getId(), currentStatus, validatedTargetStatus, record.getAiSuggestedAnswer());
            reviewStatus = REVIEW_STATUS_REVIEWED;
        } else if (DECISION_REJECT.equals(decision)) {
            updatedRows = questionDOMapper.transitStatus(current.getId(), currentStatus, validatedTargetStatus);
            reviewStatus = REVIEW_STATUS_DISCARDED;
        } else {
            return -1;
        }

        if (updatedRows <= 0) {
            return updatedRows;
        }
        int recordRows = questionValidationRecordDOMapper.updateReviewOutcome(
                record.getId(), reviewStatus, decision, currentUserId, now);
        if (recordRows <= 0) {
            throw new IllegalStateException("校验审核记录更新失败，recordId=" + record.getId()
                    + ", questionId=" + current.getId());
        }
        return updatedRows;
    }

    private String resolveValidatedTargetStatus(QuestionValidationRecordDO record, String defaultNextStatus) {
        if (record == null || record.getTaskId() == null) {
            return defaultNextStatus;
        }
        QuestionProcessTaskDO task = questionProcessTaskDOMapper.selectByPrimaryKey(record.getTaskId());
        if (task == null) {
            return defaultNextStatus;
        }
        String sourceStatus = QuestionWorkflowHelper.resolvedStatusCode(task.getSourceQuestionStatus());
        if (QuestionProcessStatusEnum.COMPLETED.getCode().equals(sourceStatus)) {
            return sourceStatus;
        }
        return defaultNextStatus;
    }

    private QuestionValidationRecordDO resolvePendingValidationRecord(QuestionDO current) {
        if (current == null || current.getId() == null) {
            return null;
        }
        QuestionValidationRecordDO latestRecord = questionValidationRecordDOMapper.selectLatestPendingByQuestionId(current.getId());
        if (latestRecord == null || latestRecord.getTaskId() == null) {
            return latestRecord;
        }
        QuestionValidationRecordDO taskRecord = questionValidationRecordDOMapper.selectPendingByTaskId(latestRecord.getTaskId());
        return taskRecord != null ? taskRecord : latestRecord;
    }

    private String normalizeReviewDecision(ReviewQuestionRequestDTO request) {
        String rawDecision = request == null ? null : StringUtils.defaultIfBlank(request.getDecision(), request.getAction());
        if (StringUtils.isBlank(rawDecision)) {
            return null;
        }
        String normalizedDecision = rawDecision.trim().toUpperCase();
        if (DECISION_APPLY_AI.equals(normalizedDecision)
                || DECISION_KEEP_ORIGINAL.equals(normalizedDecision)
                || DECISION_REJECT.equals(normalizedDecision)) {
            return normalizedDecision;
        }
        return null;
    }

}
