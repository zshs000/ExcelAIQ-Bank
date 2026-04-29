package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionCallbackInboxDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionProcessTaskDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionValidationRecordDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionCallbackInboxDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionProcessTaskDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionValidationRecordDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.enums.CallbackInboxStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionProcessStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.model.AIProcessResultMessage;
import com.zhoushuo.eaqb.question.bank.biz.rpc.DistributedIdGeneratorRpcService;
import com.zhoushuo.eaqb.question.bank.biz.state.QuestionWorkflowHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
/**
 * AI 回包落库编排服务。
 *
 * <p>职责：把 AI 的异步回包安全地落成题目主表、任务表、校验记录表和 inbox 幂等表上的一致状态变化。
 * 这个类不负责收消息本身，也不负责发送任务，而是专门处理“回包进入系统之后怎么办”。</p>
 *
 * <p>结构总览：</p>
 * <pre>
 * 对外入口
 * ├─ batchUpdateSuccessQuestions : 处理成功回包批次
 * └─ batchUpdateFailedQuestions  : 处理失败回包批次
 *
 * inbox 幂等
 * ├─ shouldProcessCallback       : 预判这条回包是否需要处理
 * ├─ beginCallbackConsumption    : 抢占回包消费权，落 inbox=RECEIVED
 * └─ completeCallbackConsumption : 消费完成，推进 inbox=PROCESSED
 *
 * task 合法性校验
 * ├─ resolveTask                 : 按 taskId 查任务
 * └─ resolveCurrentValidTask     : 校验 questionId / attemptNo / activeTask 是否匹配
 *
 * 业务落库编排
 * ├─ createValidationRecord      : VALIDATE 成功时创建校验记录
 * ├─ resolveProcessingMode       : 判定本次按 GENERATE 还是 VALIDATE 处理
 * ├─ resolveCallbackExpectedQuestionStatus : 校验题目当前状态是否允许吃回包
 * └─ updateTaskAfterCallback     : 推进 task 状态
 *
 * 辅助方法
 * ├─ shouldOverwriteAnswer       : 在 mode 双缺失时兜底推断是否覆盖答案
 * └─ parseLongOrNull             : 安全解析字符串数字
 * </pre>
 */
public class QuestionCallbackAppService {

    private static final String REVIEW_STATUS_PENDING = "PENDING";

    @Autowired
    private QuestionDOMapper questionDOMapper;

    @Autowired
    private QuestionValidationRecordDOMapper questionValidationRecordDOMapper;

    @Autowired
    private QuestionProcessTaskDOMapper questionProcessTaskDOMapper;

    @Autowired
    private QuestionCallbackInboxDOMapper questionCallbackInboxDOMapper;

    @Autowired
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;

    /**
     * 处理 AI 成功回包批次。
     * 成功回包会根据模式分成两条语义：
     * GENERATE 写正式答案，VALIDATE 写校验记录；二者都会把题目推进到 REVIEW_PENDING。
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchUpdateSuccessQuestions(Map<String, AIProcessResultMessage> successResults) {
        if (successResults == null || successResults.isEmpty()) {
            log.info("没有需要批量更新的成功题目");
            return 0;
        }

        log.info("开始批量更新成功题目状态，待处理数量: {}", successResults.size());
        int updateCount = 0;

        for (Map.Entry<String, AIProcessResultMessage> entry : successResults.entrySet()) {
            try {
                Long questionId = Long.valueOf(entry.getKey());
                AIProcessResultMessage aiResult = entry.getValue();
                if (!shouldProcessCallback(aiResult)) {
                    continue;
                }
                QuestionProcessTaskDO task = resolveCurrentValidTask(questionId, aiResult);
                if (task == null) {
                    continue;
                }
                if (!beginCallbackConsumption(aiResult)) {
                    continue;
                }

                QuestionDO current = questionDOMapper.selectByPrimaryKey(questionId);
                if (current == null) {
                    throw new IllegalStateException("成功回包对应题目不存在，questionId=" + questionId);
                }
                String expectedStatus = resolveCallbackExpectedQuestionStatus(current, task);
                if (expectedStatus == null) {
                    throw new IllegalStateException("成功回包对应题目状态不合法，questionId=" + questionId
                            + ", currentStatus=" + current.getProcessStatus());
                }

                String resolvedAiAnswer = aiResult == null ? null : aiResult.resolvedAiAnswer();
                String resolvedMode = resolveProcessingMode(aiResult, task, current);
                if (QuestionWorkflowHelper.MODE_GENERATE.equals(resolvedMode) && StringUtils.isBlank(resolvedAiAnswer)) {
                    throw new IllegalStateException("GENERATE 成功回包缺少 aiAnswer，questionId=" + questionId);
                }

                int updatedRows;
                if (StringUtils.isNotBlank(resolvedAiAnswer)
                        && QuestionWorkflowHelper.MODE_GENERATE.equals(resolvedMode)) {
                    updatedRows = questionDOMapper.transitStatusAndAnswerAndReviewMode(
                            questionId, expectedStatus, QuestionProcessStatusEnum.REVIEW_PENDING.getCode(),
                            resolvedAiAnswer, QuestionWorkflowHelper.MODE_GENERATE);
                } else {
                    if (!createValidationRecord(current, task, resolvedAiAnswer,
                            aiResult == null ? null : aiResult.getValidationResult(),
                            aiResult == null ? null : aiResult.resolvedReason())) {
                        throw new IllegalStateException("成功回包校验记录创建失败，questionId=" + questionId);
                    }
                    updatedRows = questionDOMapper.transitStatusAndReviewMode(
                            questionId, expectedStatus, QuestionProcessStatusEnum.REVIEW_PENDING.getCode(),
                            QuestionWorkflowHelper.MODE_VALIDATE);
                }
                if (updatedRows <= 0) {
                    throw new IllegalStateException("成功回包题目状态推进失败，questionId=" + questionId
                            + ", expectedStatus=" + expectedStatus);
                }
                updateTaskAfterCallback(task, "SUCCEEDED", null);
                completeCallbackConsumption(aiResult);
                updateCount++;
            } catch (NumberFormatException e) {
                log.error("题目ID格式错误: {}", entry.getKey(), e);
            } catch (Exception e) {
                log.error("单条更新失败，题目ID: {}", entry.getKey(), e);
                throw new IllegalStateException("处理成功回包失败，questionId=" + entry.getKey(), e);
            }
        }

        log.info("批量更新成功题目完成，计划更新: {}, 实际更新: {}", successResults.size(), updateCount);
        return updateCount;
    }

    /**
     * 处理 AI 失败回包批次。
     * 失败回包不会生成校验记录，而是把题目推进到 PROCESS_FAILED，并同步更新 task 失败原因。
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchUpdateFailedQuestions(Map<String, AIProcessResultMessage> errorResults) {
        if (errorResults == null || errorResults.isEmpty()) {
            log.info("没有需要批量更新的失败题目");
            return 0;
        }

        log.info("开始批量更新失败题目状态，待处理数量: {}", errorResults.size());
        int updateCount = 0;

        for (Map.Entry<String, AIProcessResultMessage> entry : errorResults.entrySet()) {
            try {
                Long questionId = Long.valueOf(entry.getKey());
                AIProcessResultMessage aiResult = entry.getValue();
                if (!shouldProcessCallback(aiResult)) {
                    continue;
                }
                QuestionProcessTaskDO task = resolveCurrentValidTask(questionId, aiResult);
                if (task == null) {
                    continue;
                }
                if (!beginCallbackConsumption(aiResult)) {
                    continue;
                }

                String reason = aiResult == null ? null : aiResult.resolvedReason();
                QuestionDO current = questionDOMapper.selectByPrimaryKey(questionId);
                if (current == null) {
                    throw new IllegalStateException("失败回包对应题目不存在，questionId=" + questionId);
                }
                String expectedStatus = resolveCallbackExpectedQuestionStatus(current, task);
                if (expectedStatus == null) {
                    throw new IllegalStateException("失败回包对应题目状态不合法，questionId=" + questionId
                            + ", currentStatus=" + current.getProcessStatus());
                }

                int updatedRows = questionDOMapper.transitStatus(
                        questionId, expectedStatus, QuestionProcessStatusEnum.PROCESS_FAILED.getCode());
                if (updatedRows <= 0) {
                    throw new IllegalStateException("失败回包题目状态推进失败，questionId=" + questionId
                            + ", expectedStatus=" + expectedStatus);
                }
                updateTaskAfterCallback(task, "FAILED", reason);
                completeCallbackConsumption(aiResult);
                updateCount++;
            } catch (NumberFormatException e) {
                log.error("题目ID格式错误: {}", entry.getKey(), e);
            } catch (Exception e) {
                log.error("单条更新失败，题目ID: {}", entry.getKey(), e);
                throw new IllegalStateException("处理失败回包失败，questionId=" + entry.getKey(), e);
            }
        }

        log.info("批量更新失败题目完成，计划更新: {}, 实际更新: {}", errorResults.size(), updateCount);
        return updateCount;
    }

    /**
     * 预判这条回包是否需要处理。
     * 如果 inbox 中已经存在且状态为 PROCESSED，说明这是重复回包，直接忽略。
     */
    private boolean shouldProcessCallback(AIProcessResultMessage aiResult) {
        String callbackKey = aiResult == null ? null : aiResult.resolvedCallbackKey();
        if (StringUtils.isBlank(callbackKey)) {
            throw new IllegalStateException("回包缺少 callbackKey/taskId，无法建立 inbox 幂等");
        }
        QuestionCallbackInboxDO existing = questionCallbackInboxDOMapper.selectByCallbackKey(callbackKey);
        if (existing == null) {
            return true;
        }
        if (CallbackInboxStatusEnum.PROCESSED.getCode().equals(existing.getConsumeStatus())) {
            log.info("重复回包已忽略，callbackKey={}", callbackKey);
            return false;
        }
        throw new IllegalStateException("回包 inbox 状态异常，callbackKey=" + callbackKey
                + ", status=" + existing.getConsumeStatus());
    }

    /**
     * 开始消费回包。
     * 通过插入 inbox=RECEIVED 记录抢占消费权，防止同一 callback 被并发重复处理。
     */
    private boolean beginCallbackConsumption(AIProcessResultMessage aiResult) {
        String callbackKey = aiResult == null ? null : aiResult.resolvedCallbackKey();
        if (StringUtils.isBlank(callbackKey)) {
            throw new IllegalStateException("回包缺少 callbackKey/taskId，无法建立 inbox 幂等");
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            QuestionCallbackInboxDO inbox = QuestionCallbackInboxDO.builder()
                    .id(Long.valueOf(distributedIdGeneratorRpcService.nextQuestionBankEntityId()))
                    .callbackKey(callbackKey)
                    .taskId(parseLongOrNull(aiResult == null ? null : aiResult.resolvedTaskId()))
                    .consumeStatus(CallbackInboxStatusEnum.RECEIVED.getCode())
                    .createdTime(now)
                    .updatedTime(now)
                    .build();
            int insertedRows = questionCallbackInboxDOMapper.insert(inbox);
            if (insertedRows <= 0) {
                throw new IllegalStateException("回包 inbox 插入失败，callbackKey=" + callbackKey);
            }
            return true;
        } catch (DataIntegrityViolationException e) {
            QuestionCallbackInboxDO duplicated = questionCallbackInboxDOMapper.selectByCallbackKey(callbackKey);
            if (duplicated != null && CallbackInboxStatusEnum.PROCESSED.getCode().equals(duplicated.getConsumeStatus())) {
                log.info("并发重复回包已忽略，callbackKey={}", callbackKey);
                return false;
            }
            // 当前无法确认这条回包已经被安全处理完成（这里的 DataIntegrityViolationException
            // 也不一定只来自 callbackKey 唯一约束冲突），因此继续抛出让 MQ 将本次消费视为失败，
            // 后续再重投，避免把未完成的回包误吞掉。
            throw e;
        }
    }

    /**
     * 完成回包消费。
     * 只有题目、task、校验记录等主流程都处理完成后，才把 inbox 推进到 PROCESSED。
     */
    private void completeCallbackConsumption(AIProcessResultMessage aiResult) {
        String callbackKey = aiResult == null ? null : aiResult.resolvedCallbackKey();
        if (StringUtils.isBlank(callbackKey)) {
            throw new IllegalStateException("回包缺少 callbackKey/taskId，无法完成 inbox 状态");
        }
        int updatedRows = questionCallbackInboxDOMapper.updateConsumeStatus(
                callbackKey,
                CallbackInboxStatusEnum.RECEIVED.getCode(),
                CallbackInboxStatusEnum.PROCESSED.getCode()
        );
        if (updatedRows <= 0) {
            throw new IllegalStateException("回包 inbox 状态推进失败，callbackKey=" + callbackKey);
        }
    }

    /**
     * 为 VALIDATE 成功回包创建校验记录。
     * 这里保留原答案快照、AI 建议答案、校验结论和原因，供后续人工审核使用。
     */
    private boolean createValidationRecord(QuestionDO current, QuestionProcessTaskDO task, String aiSuggestedAnswer,
                                           String validationResult, String reason) {
        if (current == null || current.getId() == null) {
            return false;
        }
        Long recordId = Long.valueOf(distributedIdGeneratorRpcService.nextQuestionBankEntityId());
        LocalDateTime now = LocalDateTime.now();
        QuestionValidationRecordDO record = QuestionValidationRecordDO.builder()
                .id(recordId)
                .questionId(current.getId())
                .taskId(task == null ? null : task.getId())
                .originalAnswerSnapshot(current.getAnswer())
                .aiSuggestedAnswer(aiSuggestedAnswer)
                .validationResult(StringUtils.defaultIfBlank(validationResult, "NA"))
                .reason(reason)
                .reviewStatus(REVIEW_STATUS_PENDING)
                .createdTime(now)
                .updatedTime(now)
                .build();
        return questionValidationRecordDOMapper.insert(record) > 0;
    }

    /**
     * 按回包中的 taskId 解析任务。
     * taskId 缺失或非法时返回 null，由上层决定跳过这条回包。
     */
    private QuestionProcessTaskDO resolveTask(AIProcessResultMessage aiResult) {
        if (aiResult == null || StringUtils.isBlank(aiResult.resolvedTaskId())) {
            return null;
        }
        try {
            return questionProcessTaskDOMapper.selectByPrimaryKey(Long.valueOf(aiResult.resolvedTaskId()));
        } catch (NumberFormatException e) {
            log.warn("回包 taskId 非法，taskId={}", aiResult.resolvedTaskId(), e);
            return null;
        }
    }

    /**
     * 校验这条回包是否命中“当前有效任务”。
     * 会同时检查 questionId、attemptNo，以及这条 task 是否仍是当前 activeTask，
     * 以避免旧回包、串题回包或错轮次回包污染当前题目。
     */
    private QuestionProcessTaskDO resolveCurrentValidTask(Long questionId, AIProcessResultMessage aiResult) {
        QuestionProcessTaskDO task = resolveTask(aiResult);
        if (task == null) {
            log.warn("回包未命中任务，questionId={}, taskId={}",
                    questionId, aiResult == null ? null : aiResult.resolvedTaskId());
            return null;
        }
        if (!questionId.equals(task.getQuestionId())) {
            log.warn("回包 task 与题目不匹配，questionId={}, taskQuestionId={}, taskId={}",
                    questionId, task.getQuestionId(), task.getId());
            return null;
        }
        if (task.getAttemptNo() == null || task.getAttemptNo() != aiResult.resolvedAttemptNo()) {
            log.warn("回包 attemptNo 与任务不匹配，questionId={}, taskId={}, messageAttemptNo={}, taskAttemptNo={}",
                    questionId, task.getId(), aiResult == null ? null : aiResult.resolvedAttemptNo(), task.getAttemptNo());
            return null;
        }
        QuestionProcessTaskDO activeTask = questionProcessTaskDOMapper.selectActiveTaskByQuestionId(questionId);
        if (activeTask == null) {
            log.warn("回包未命中当前有效任务，questionId={}, taskId={}", questionId, task.getId());
            return null;
        }
        if (!task.getId().equals(activeTask.getId())) {
            log.warn("回包命中旧任务，questionId={}, taskId={}, activeTaskId={}",
                    questionId, task.getId(), activeTask.getId());
            return null;
        }
        return task;
    }

    /**
     * 解析本次回包应按哪种模式处理。
     * 优先信任 task.mode，其次看消息 mode；只有两者都缺失时，才进入兜底推断。
     */
    private String resolveProcessingMode(AIProcessResultMessage aiResult, QuestionProcessTaskDO task, QuestionDO current) {
        if (task != null && StringUtils.isNotBlank(task.getMode())) {
            return QuestionWorkflowHelper.normalizeModeOrFallback(
                    task.getMode(), QuestionWorkflowHelper.MODE_GENERATE);
        }
        if (aiResult != null && StringUtils.isNotBlank(aiResult.getMode())) {
            return QuestionWorkflowHelper.normalizeModeOrFallback(
                    aiResult.getMode(), QuestionWorkflowHelper.MODE_GENERATE);
        }
        return shouldOverwriteAnswer(aiResult, current)
                ? QuestionWorkflowHelper.MODE_GENERATE
                : QuestionWorkflowHelper.MODE_VALIDATE;
    }

    /**
     * 解析回包期望命中的题目状态。
     * 当前只允许处理落在 PROCESSING 或部分 DISPATCHING 场景上的回包，
     * 不合法时返回 null，由上层按确定性业务错误处理。
     */
    private String resolveCallbackExpectedQuestionStatus(QuestionDO current, QuestionProcessTaskDO task) {
        if (current == null) {
            return null;
        }
        String currentStatus = QuestionWorkflowHelper.resolvedStatusCode(current.getProcessStatus());
        if (QuestionProcessStatusEnum.PROCESSING.getCode().equals(currentStatus)) {
            return currentStatus;
        }
        if (task != null && QuestionProcessStatusEnum.DISPATCHING.getCode().equals(currentStatus)) {
            return currentStatus;
        }
        return null;
    }

    /**
     * 在题目落库成功后推进 task 状态。
     * 这里要求按旧状态做条件更新，避免并发下把已经变化的 task 再次错误推进。
     */
    private void updateTaskAfterCallback(QuestionProcessTaskDO task, String targetStatus, String failureReason) {
        if (task == null || task.getId() == null || StringUtils.isBlank(task.getTaskStatus())) {
            throw new IllegalStateException("回包对应任务为空，无法推进 task 状态");
        }
        int updatedRows = questionProcessTaskDOMapper.updateTaskStatus(
                task.getId(), task.getTaskStatus(), targetStatus, failureReason);
        if (updatedRows <= 0) {
            throw new IllegalStateException("回包 task 状态推进失败，taskId=" + task.getId()
                    + ", expectedStatus=" + task.getTaskStatus()
                    + ", targetStatus=" + targetStatus);
        }
    }

    /**
     * 在 task.mode 和 message.mode 都缺失时，基于消息形态启发式推断是否应该覆盖正式答案。
     *
     * <p>这里叫“启发式”，是因为它不是协议里的强约束规则，而是在模式信息完全缺失时，
     * 用现有字段特征做兜底判断。</p>
     *
     * <p>当前判断顺序：</p>
     * <pre>
     * 1. validationResult 有明确值（非 NA） -> 更像校验结果，不覆盖答案
     * 2. 题目当前没有正式答案 -> 更像生成链路，覆盖答案
     * 3. 其余情况默认更保守地按校验链路处理，不覆盖答案
     * </pre>
     */
    private boolean shouldOverwriteAnswer(AIProcessResultMessage aiResult, QuestionDO current) {
        if (aiResult == null) {
            return false;
        }

        String validationResult = StringUtils.upperCase(StringUtils.trimToNull(aiResult.getValidationResult()));
        if (StringUtils.isNotBlank(validationResult) && !"NA".equals(validationResult)) {
            return false;
        }
        return current == null || StringUtils.isBlank(current.getAnswer());
    }

    /**
     * 将字符串解析为 Long。
     * 空值返回 null，非法数字直接抛错，避免把脏数据悄悄吞掉。
     */
    private Long parseLongOrNull(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("非法数字值: " + value, e);
        }
    }
}
