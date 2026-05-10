package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionValidationRecordDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionValidationRecordDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionProcessStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionStatusActionEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.question.bank.biz.model.vo.SendToQueueResultVO;
import com.zhoushuo.eaqb.question.bank.biz.rpc.DistributedIdGeneratorRpcService;
import com.zhoushuo.eaqb.question.bank.biz.service.QuestionDispatchService;
import com.zhoushuo.eaqb.question.bank.biz.state.QuestionStatusStateMachine;
import com.zhoushuo.eaqb.question.bank.biz.state.QuestionWorkflowHelper;
import com.zhoushuo.framework.common.exception.BizException;
import com.zhoushuo.framework.common.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
/**
 * 题目派发应用服务。
 * 负责承接“把题目送入 AI 处理链路”的应用层编排：
 * 做权限与状态校验、按模式筛题、抢占派发状态，并委托下游可靠派发服务或本地 mock 执行。
 */
public class QuestionDispatchAppService {

    private static final String REVIEW_STATUS_PENDING = "PENDING";

    @Autowired
    private QuestionDOMapper questionDOMapper;

    @Autowired
    private QuestionValidationRecordDOMapper questionValidationRecordDOMapper;

    @Autowired
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;

    @Autowired(required = false)
    private QuestionDispatchService questionDispatchService;

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private QuestionAccessSupport questionAccessSupport;

    @Value("${feature.mq.mock-enabled:false}")
    private boolean mqMockEnabled;

    /**
     * 批量把题目提交到 AI 异步处理链路。
     *
     * <p>注意：这里返回的是“本次请求的受理摘要”，不是“MQ 实际发送结果”。</p>
     *
     * <p>在真实 outbox 链路下，本方法只负责：</p>
     * <p>1. 做权限、状态、模式校验；</p>
     * <p>2. 为可处理题目创建 task；</p>
     * <p>3. 写入 outbox 事件；</p>
     * <p>4. 返回有多少题目成功进入异步链路。</p>
     *
     * <p>真正的 MQ 投递由后续 outbox 扫描器异步执行，因此返回值里的 eligibleCount
     * 可以理解为“成功受理数”；sentCount 在真实链路下固定为 0，不表示失败，只表示当前接口返回时尚未执行真正派发。</p>
     *
     * <p>支持两种模式：</p>
     * <p>GENERATE：题目暂无答案，交给 AI 生成答案；</p>
     * <p>VALIDATE：题目已有答案，交给 AI 做校验并产出建议结果。</p>
     */
    public Response<?> sendQuestionsToQueue(List<Long> questionIds, String mode) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Response.fail("题目ID列表不能为空");
        }
        String normalizedMode = QuestionWorkflowHelper.normalizeMode(mode);
        if (normalizedMode == null) {
            return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), "mode 仅支持 GENERATE 或 VALIDATE");
        }
        Long currentUserId = questionAccessSupport.requireCurrentUserId();
        log.info("开始批量发送题目到消息队列，题目数量: {}, mode: {}", questionIds.size(), normalizedMode);

        try {
            List<QuestionDO> questions = questionDOMapper.selectBatchByIds(questionIds);
            log.info("查询题目信息成功，查询数量: {}", questions.size());

            List<QuestionDO> ownedQuestions = questions.stream()
                    .filter(q -> currentUserId.equals(q.getCreatedBy()))
                    .collect(Collectors.toList());
            int skippedPermissionCount = questions.size() - ownedQuestions.size();

            int skippedStatusCount = 0;
            int skippedHasAnswerCount = 0;
            int skippedNoAnswerCount = 0;

            List<QuestionDO> questionsToSend = new ArrayList<>();
            for (QuestionDO question : ownedQuestions) {
                if (!isSendModeAllowedForQuestion(question, normalizedMode)) {
                    skippedStatusCount++;
                    continue;
                }
                if (!QuestionStatusStateMachine.canTransit(question.getProcessStatus(), QuestionStatusActionEnum.SEND)) {
                    skippedStatusCount++;
                    continue;
                }
                if (QuestionWorkflowHelper.MODE_GENERATE.equals(normalizedMode) && hasAnswer(question)) {
                    skippedHasAnswerCount++;
                    continue;
                }
                if (QuestionWorkflowHelper.MODE_VALIDATE.equals(normalizedMode) && !hasAnswer(question)) {
                    skippedNoAnswerCount++;
                    continue;
                }
                questionsToSend.add(question);
            }

            int skippedCount = skippedStatusCount + skippedPermissionCount + skippedHasAnswerCount + skippedNoAnswerCount;
            if (questionsToSend.isEmpty()) {
                String noEligibleMessage = "没有可发送题目：当前状态不允许发送";
                if (skippedPermissionCount > 0) {
                    noEligibleMessage = noEligibleMessage + "，且存在无权限题目";
                }
                return Response.success(buildSendResult(normalizedMode, questionIds.size(), questions.size(),
                        0, 0, skippedCount, skippedHasAnswerCount, skippedNoAnswerCount, noEligibleMessage));
            }

            if (rocketMQTemplate == null) {
                if (!mqMockEnabled) {
                    return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(),
                            "当前环境未启用 MQ，请开启 feature.mq.enabled 后再发送");
                }
                List<QuestionDO> lockedQuestions = new ArrayList<>();
                for (QuestionDO question : questionsToSend) {
                    if (markQuestionDispatching(question)) {
                        lockedQuestions.add(question);
                        continue;
                    }
                    skippedStatusCount++;
                    skippedCount++;
                    log.warn("题目状态在发送前已变化，跳过本地模拟，questionId={}, currentStatus={}",
                            question.getId(), question.getProcessStatus());
                }
                if (lockedQuestions.isEmpty()) {
                    return Response.success(buildSendResult(normalizedMode, questionIds.size(), questions.size(),
                            0, 0, skippedCount, skippedHasAnswerCount, skippedNoAnswerCount,
                            "没有可发送题目：题目状态已变化或当前状态不允许发送"));
                }
                int mockSuccessCount = mockProcessQuestions(lockedQuestions, normalizedMode);
                String mockMessage = String.format("MQ未启用，已执行本地模拟处理，成功 %d/%d 条",
                        mockSuccessCount, lockedQuestions.size());
                log.info(mockMessage);
                return Response.success(buildSendResult(normalizedMode, questionIds.size(), questions.size(),
                        lockedQuestions.size(), mockSuccessCount, skippedCount,
                        skippedHasAnswerCount, skippedNoAnswerCount, mockMessage));
            }

            if (questionDispatchService == null) {
                return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(),
                        "当前环境未启用 QuestionDispatchService，请先配置可靠派发链路后再发送");
            }

            int preparedCount = 0;
            for (QuestionDO question : questionsToSend) {
                Long taskId = questionDispatchService.prepareQuestionDispatch(question, normalizedMode);
                if (taskId != null) {
                    preparedCount++;
                    continue;
                }
                skippedStatusCount++;
                skippedCount++;
                log.warn("题目在准备派发时被跳过，questionId={}, currentStatus={}",
                        question.getId(), question.getProcessStatus());
            }

            if (preparedCount == 0) {
                return Response.success(buildSendResult(normalizedMode, questionIds.size(), questions.size(),
                        0, 0, skippedCount, skippedHasAnswerCount, skippedNoAnswerCount,
                        "没有可发送题目：题目状态已变化或存在有效任务"));
            }

            String dispatchMessage = String.format("题目已提交异步处理 %d 条，正在后台处理中", preparedCount);
            return Response.success(buildSendResult(normalizedMode, questionIds.size(), questions.size(),
                    preparedCount, 0, skippedCount, skippedHasAnswerCount, skippedNoAnswerCount, dispatchMessage));
        } catch (Exception e) {
            log.error("批量发送题目到消息队列失败", e);
            throw new BizException(ResponseCodeEnum.QUESTION_SEND_FAILED);
        }
    }

    /**
     * 本地 mock 模式下直接模拟“派发成功 -> AI 成功回包”后的落库效果，
     * 方便在未接入 MQ 的环境中联调题目流程。
     */
    private int mockProcessQuestions(List<QuestionDO> questionsToSend, String mode) {
        int successCount = 0;
        String nextStatus = QuestionWorkflowHelper.nextStatusCodeOrNull(
                QuestionProcessStatusEnum.PROCESSING.getCode(), QuestionStatusActionEnum.AI_SUCCESS);
        if (nextStatus == null) {
            log.error("状态机配置错误：PROCESSING 无法通过 AI_SUCCESS 流转");
            return 0;
        }
        for (QuestionDO question : questionsToSend) {
            if (!markQuestionProcessingAfterDispatch(question.getId())) {
                log.warn("本地 mock 处理前状态推进失败，questionId={}", question.getId());
                continue;
            }
            if (QuestionWorkflowHelper.MODE_GENERATE.equals(mode)) {
                if (questionDOMapper.transitStatusAndAnswerAndReviewMode(question.getId(),
                        QuestionProcessStatusEnum.PROCESSING.getCode(), nextStatus,
                        buildMockAnswer(question), QuestionWorkflowHelper.MODE_GENERATE) > 0) {
                    successCount++;
                }
                continue;
            }
            if (createValidationRecord(question, buildMockAnswer(question), "UNCERTAIN", "本地 mock 校验结果")
                    && questionDOMapper.transitStatusAndReviewMode(question.getId(),
                    QuestionProcessStatusEnum.PROCESSING.getCode(), nextStatus, QuestionWorkflowHelper.MODE_VALIDATE) > 0) {
                successCount++;
            }
        }
        return successCount;
    }

    /**
     * VALIDATE 模式下保存一条待审核的校验记录，供后续人工审核使用。
     */
    private boolean createValidationRecord(QuestionDO current, String aiSuggestedAnswer,
                                           String validationResult, String reason) {
        if (current == null || current.getId() == null) {
            return false;
        }
        Long recordId = Long.valueOf(distributedIdGeneratorRpcService.nextQuestionBankEntityId());
        LocalDateTime now = LocalDateTime.now();
        QuestionValidationRecordDO record = QuestionValidationRecordDO.builder()
                .id(recordId)
                .questionId(current.getId())
                .taskId(null)
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

    private String buildMockAnswer(QuestionDO question) {
        String content = question == null ? "" : StringUtils.defaultString(question.getContent());
        String preview = content.length() > 30 ? content.substring(0, 30) + "..." : content;
        return "【MOCK-AI】" + preview;
    }

    /**
     * 统一组装派发结果摘要，返回给调用方展示“命中多少、派发多少、跳过多少”。
     */
    private SendToQueueResultVO buildSendResult(String mode, int requestedCount, int foundCount,
                                                int eligibleCount, int sentCount, int skippedCount,
                                                int skippedHasAnswerCount, int skippedNoAnswerCount,
                                                String message) {
        return SendToQueueResultVO.builder()
                .mode(mode)
                .requestedCount(requestedCount)
                .foundCount(foundCount)
                .eligibleCount(eligibleCount)
                .sentCount(sentCount)
                .skippedCount(skippedCount)
                .skippedHasAnswerCount(skippedHasAnswerCount)
                .skippedNoAnswerCount(skippedNoAnswerCount)
                .message(message)
                .build();
    }

    /**
     * 在真正派发前先把题目状态推进到 DISPATCHING，起到“抢占派发资格”的作用。
     */
    private boolean markQuestionDispatching(QuestionDO question) {
        if (question == null || question.getId() == null) {
            return false;
        }
        String currentStatus = QuestionWorkflowHelper.resolvedStatusCode(question.getProcessStatus());
        String nextStatus = QuestionWorkflowHelper.nextStatusCodeOrNull(currentStatus, QuestionStatusActionEnum.SEND);
        if (nextStatus == null) {
            return false;
        }
        return questionDOMapper.transitStatus(question.getId(), currentStatus, nextStatus) > 0;
    }

    /**
     * mock 路径里补一次派发成功后的状态推进，使状态机表现与真实异步链路保持一致。
     */
    private boolean markQuestionProcessingAfterDispatch(Long questionId) {
        if (questionId == null) {
            return false;
        }
        String nextStatus = QuestionWorkflowHelper.nextStatusCodeOrNull(QuestionProcessStatusEnum.DISPATCHING.getCode(),
                QuestionStatusActionEnum.SEND_SUCCESS);
        if (nextStatus == null) {
            return false;
        }
        return questionDOMapper.transitStatus(questionId, QuestionProcessStatusEnum.DISPATCHING.getCode(), nextStatus) > 0;
    }

    private boolean hasAnswer(QuestionDO question) {
        return question != null && StringUtils.isNotBlank(question.getAnswer());
    }

    /**
     * 按发送模式判断题目是否允许进入 AI 链路。
     */
    private boolean isSendModeAllowedForQuestion(QuestionDO question, String mode) {
        if (question == null || StringUtils.isBlank(mode)) {
            return false;
        }
        String currentStatus = QuestionWorkflowHelper.resolvedStatusCode(question.getProcessStatus());
        if (QuestionWorkflowHelper.MODE_GENERATE.equals(mode)) {
            return QuestionProcessStatusEnum.WAITING.getCode().equals(currentStatus);
        }
        if (QuestionWorkflowHelper.MODE_VALIDATE.equals(mode)) {
            return QuestionProcessStatusEnum.WAITING.getCode().equals(currentStatus)
                    || QuestionProcessStatusEnum.COMPLETED.getCode().equals(currentStatus);
        }
        return false;
    }

}
