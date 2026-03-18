package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.zhoushuo.eaqb.question.bank.biz.constant.MQConstants;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionProcessStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionStatusActionEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.CreateQuestionDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.QuestionPageQueryDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.ReviewQuestionRequestDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.UpdateQuestionDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.vo.QuestionVO;
import com.zhoushuo.eaqb.question.bank.biz.model.vo.SendToQueueResultVO;
import com.zhoushuo.eaqb.question.bank.biz.model.AIProcessResultMessage;
import com.zhoushuo.eaqb.question.bank.biz.rpc.DistributedIdGeneratorRpcService;
import com.zhoushuo.eaqb.question.bank.biz.service.QuestionService;
import com.zhoushuo.eaqb.question.bank.biz.state.QuestionStatusStateMachine;
import com.zhoushuo.eaqb.question.bank.req.BatchImportQuestionRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.QuestionDTO;
import com.zhoushuo.eaqb.question.bank.resp.BatchImportQuestionResponseDTO;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.zhoushuo.eaqb.question.bank.biz.model.QuestionMessage;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

@Slf4j
@Service
public class QuestionServiceImpl implements QuestionService {
    private static final String MODE_GENERATE = "GENERATE";
    private static final String MODE_VALIDATE = "VALIDATE";
    private static final List<String> MUTABLE_STATUSES = List.of(
            QuestionProcessStatusEnum.WAITING.getCode(),
            QuestionProcessStatusEnum.PROCESS_FAILED.getCode()
    );
    
    // 现有注入
    @Autowired
    private QuestionDOMapper questionDOMapper;
    
    @Autowired
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    
    // 添加RocketMQTemplate注入
    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    @Value("${feature.mq.mock-enabled:false}")
    private boolean mqMockEnabled;
    

    
    // 实现发送消息到队列的方法
    @Override
    public Response<?> sendQuestionsToQueue(List<Long> questionIds, String mode) {
        // 参数校验
        if (questionIds == null || questionIds.isEmpty()) {
            return Response.fail("题目ID列表不能为空");
        }
        Long currentUserId = requiredUserId();
        String normalizedMode = normalizeMode(mode);
        if (normalizedMode == null) {
            return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), "mode 仅支持 GENERATE 或 VALIDATE");
        }
        log.info("开始批量发送题目到消息队列，题目数量: {}, mode: {}", questionIds.size(), normalizedMode);
        
        try {
            // 1. 批量查询题目信息
            List<QuestionDO> questions = questionDOMapper.selectBatchByIds(questionIds);
            log.info("查询题目信息成功，查询数量: {}", questions.size());

            // 2. 权限过滤：仅允许发送当前用户自己的题目。
            List<QuestionDO> ownedQuestions = questions.stream()
                    .filter(q -> currentUserId.equals(q.getCreatedBy()))
                    .collect(Collectors.toList());
            int skippedPermissionCount = questions.size() - ownedQuestions.size();

            int skippedStatusCount = 0;
            int skippedHasAnswerCount = 0;
            int skippedNoAnswerCount = 0;

            // 3. 仅允许符合当前模式且状态机允许 SEND 的题目进入处理队列。
            List<QuestionDO> questionsToSend = new ArrayList<>();
            for (QuestionDO question : ownedQuestions) {
                if (!QuestionStatusStateMachine.canTransit(question.getProcessStatus(), QuestionStatusActionEnum.SEND)) {
                    skippedStatusCount++;
                    continue;
                }
                if (MODE_GENERATE.equals(normalizedMode) && hasAnswer(question)) {
                    skippedHasAnswerCount++;
                    continue;
                }
                if (MODE_VALIDATE.equals(normalizedMode) && !hasAnswer(question)) {
                    skippedNoAnswerCount++;
                    continue;
                }
                questionsToSend.add(question);
            }

            int skippedCount = skippedStatusCount + skippedPermissionCount + skippedHasAnswerCount + skippedNoAnswerCount;

            // 4. MQ 不可用且 mock 关闭时，直接返回错误，不提前改状态。
            if (rocketMQTemplate == null && !mqMockEnabled) {
                return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(),
                        "当前环境未启用 MQ，请开启 feature.mq.enabled 后再发送");
            }

            if (questionsToSend.isEmpty()) {
                String noEligibleMessage = "没有可发送题目：当前状态不允许发送";
                if (skippedPermissionCount > 0) {
                    noEligibleMessage = noEligibleMessage + "，且存在无权限题目";
                }
                return Response.success(buildSendResult(normalizedMode, questionIds.size(), questions.size(),
                        0, 0, skippedCount, skippedHasAnswerCount, skippedNoAnswerCount, noEligibleMessage));
            }

            List<QuestionDO> lockedQuestions = new ArrayList<>();
            for (QuestionDO question : questionsToSend) {
                if (markQuestionProcessing(question)) {
                    lockedQuestions.add(question);
                    continue;
                }
                skippedStatusCount++;
                skippedCount++;
                log.warn("题目状态在发送前已变化，跳过发送，questionId={}, currentStatus={}",
                        question.getId(), question.getProcessStatus());
            }

            if (lockedQuestions.isEmpty()) {
                return Response.success(buildSendResult(normalizedMode, questionIds.size(), questions.size(),
                        0, 0, skippedCount, skippedHasAnswerCount, skippedNoAnswerCount,
                        "没有可发送题目：题目状态已变化或当前状态不允许发送"));
            }

            if (rocketMQTemplate == null) {
                int mockSuccessCount = mockProcessQuestions(lockedQuestions, normalizedMode);
                String mockMessage = String.format("MQ未启用，已执行本地模拟处理，成功 %d/%d 条",
                        mockSuccessCount, lockedQuestions.size());
                log.info(mockMessage);
                return Response.success(buildSendResult(normalizedMode, questionIds.size(), questions.size(),
                        lockedQuestions.size(), mockSuccessCount, skippedCount,
                        skippedHasAnswerCount, skippedNoAnswerCount, mockMessage));
            }
            
            int sendSuccessCount = 0;
            int sendFailedCount = 0;
            for (QuestionDO question : lockedQuestions) {
                try {
                    QuestionMessage message = new QuestionMessage(
                            String.valueOf(question.getId()),
                            question.getContent(),
                            normalizedMode,
                            MODE_VALIDATE.equals(normalizedMode) ? question.getAnswer() : null
                    );
                    
                    Message<QuestionMessage> msg = MessageBuilder.withPayload(message).build();
                    log.info("发送消息对象: {}", msg);
                    
                    rocketMQTemplate.syncSend(MQConstants.TOPIC_TEST, msg);
                    
                    sendSuccessCount++;
                    log.info("题目ID: {} 发送到队列成功", question.getId());
                } catch (Exception e) {
                    log.error("题目ID: {} 发送到队列失败", question.getId(), e);
                    rollbackProcessingQuestion(question.getId());
                    sendFailedCount++;
                }
            }

            if (sendFailedCount > 0) {
                skippedCount += sendFailedCount;
            }
            if (sendSuccessCount == 0 && sendFailedCount > 0) {
                return Response.fail(ResponseCodeEnum.QUESTION_SEND_FAILED.getErrorCode(),
                        String.format("题目发送失败，失败 %d 条，已回滚为 WAITING", sendFailedCount));
            }
            String sendMessage = sendFailedCount > 0
                    ? String.format("题目发送部分成功，成功 %d 条，失败 %d 条（已回滚）", sendSuccessCount, sendFailedCount)
                    : "题目发送完成";
            return Response.success(buildSendResult(normalizedMode, questionIds.size(), questions.size(),
                    lockedQuestions.size(), sendSuccessCount, skippedCount,
                    skippedHasAnswerCount, skippedNoAnswerCount, sendMessage));
            
        } catch (Exception e) {
            log.error("批量发送题目到消息队列失败", e);
            throw new BizException(ResponseCodeEnum.QUESTION_SEND_FAILED);
        }
    }

    private int mockProcessQuestions(List<QuestionDO> questionsToSend, String mode) {
        int successCount = 0;
        String nextStatus = nextStatusCodeOrNull(QuestionProcessStatusEnum.PROCESSING.getCode(), QuestionStatusActionEnum.AI_SUCCESS);
        if (nextStatus == null) {
            log.error("状态机配置错误：PROCESSING 无法通过 AI_SUCCESS 流转");
            return 0;
        }
        for (QuestionDO question : questionsToSend) {
            if (MODE_GENERATE.equals(mode)) {
                if (questionDOMapper.transitStatusAndAnswer(question.getId(),
                        QuestionProcessStatusEnum.PROCESSING.getCode(), nextStatus, buildMockAnswer(question)) > 0) {
                    successCount++;
                }
                continue;
            }
            if (questionDOMapper.transitStatus(question.getId(),
                    QuestionProcessStatusEnum.PROCESSING.getCode(), nextStatus) > 0) {
                successCount++;
            }
        }
        return successCount;
    }

    private String buildMockAnswer(QuestionDO question) {
        String content = question == null ? "" : StringUtils.defaultString(question.getContent());
        String preview = content.length() > 30 ? content.substring(0, 30) + "..." : content;
        return "【MOCK-AI】" + preview;
    }

    private String nextStatusCodeOrNull(String currentStatus, QuestionStatusActionEnum action) {
        Optional<QuestionProcessStatusEnum> next = QuestionStatusStateMachine.next(currentStatus, action);
        return next.map(QuestionProcessStatusEnum::getCode).orElse(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<BatchImportQuestionResponseDTO> batchImportQuestions(BatchImportQuestionRequestDTO request) {
        log.info("开始批量导入题目，请求数量: {}", request != null ? request.getQuestions().size() : 0);

        // 参数空检查（基础防御）
        if (request == null || request.getQuestions() == null || request.getQuestions().isEmpty()) {
            log.warn("批量导入题目参数为空");
            return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), "导入题目列表不能为空");
        }

        Long currentUserId = requiredUserId();
        int totalCount = request.getQuestions().size();
        log.info("当前用户ID: {}, 导入题目数量: {}", currentUserId, totalCount);

        try {
            // 数据转换和ID生成
            List<QuestionDO> questionDOList = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            for (QuestionDTO dto : request.getQuestions()) {
                // 调用分布式ID生成器
                Long questionId = Long.valueOf(distributedIdGeneratorRpcService.getQuestionBankId());
                if (questionId == null) {
                    throw new RuntimeException("生成题目ID失败");
                }

                QuestionDO questionDO = QuestionDO.builder()
                        .id(questionId)
                        .content(dto.getContent())
                        .answer(dto.getAnswer())
                        .analysis(dto.getAnalysis())
                        .processStatus(QuestionProcessStatusEnum.WAITING.getCode())
                        .createdBy(currentUserId)
                        .createdTime(now)
                        .updatedTime(now)
                        .build();

                questionDOList.add(questionDO);
            }

            // 批量插入数据库
            int successCount = questionDOMapper.batchInsert(questionDOList);

            log.info("批量导入题目成功，用户ID: {}, 成功数量: {}", currentUserId, successCount);

            BatchImportQuestionResponseDTO response = BatchImportQuestionResponseDTO.builder()
                    .success(true)
                    .totalCount(totalCount)
                    .successCount(successCount)
                    .failedCount(0)
                    .build();

            return Response.success(response);

        } catch (Exception e) {
            // 系统错误处理
            log.error("批量导入题目系统错误，用户ID: {}", currentUserId, e);

            // 针对不同的系统错误提供更具体的错误消息
            String errorMsg = "导入失败，请稍后重试";
            if (e instanceof RuntimeException && StringUtils.isNotBlank(e.getMessage())) {
                errorMsg = e.getMessage();
            } else if (e instanceof DataIntegrityViolationException) {
                errorMsg = "数据库约束冲突，请检查数据";
            } else if (e instanceof SQLException) {
                errorMsg = "数据库操作失败，请稍后重试";
            }

            return Response.fail(ResponseCodeEnum.SYSTEM_ERROR.getErrorCode(), errorMsg);
        }
    }

    private String normalizeMode(String mode) {
        if (StringUtils.isBlank(mode)) {
            return MODE_GENERATE;
        }
        String requestedMode = mode.trim().toUpperCase();
        if (!MODE_GENERATE.equals(requestedMode) && !MODE_VALIDATE.equals(requestedMode)) {
            log.warn("收到非法 mode={}", requestedMode);
            return null;
        }
        return requestedMode;
    }

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
     * 创建题目
     *
     * @param request
     * @return
     */

    @Override
    public Response<QuestionVO> createQuestion(CreateQuestionDTO request) {
        log.info("开始创建题目，请求参数: {}", request);
        //获取用户ID
        Long currentUserId = requiredUserId();

        // 生成题目ID
        Long questionId = Long.valueOf(distributedIdGeneratorRpcService.getQuestionBankId());
        if (questionId == null) {
            log.error("生成题目ID失败");
            throw new BizException(ResponseCodeEnum.QUESTION_ID_GENERATE_FAILED);
        }

        QuestionDO questionDO = QuestionDO.builder()
                .id(questionId)
                .content(request.getContent())
                .answer(request.getAnswer())
                .analysis(request.getAnalysis())
                .processStatus(QuestionProcessStatusEnum.WAITING.getCode())
                .createdBy(currentUserId)
                .createdTime(LocalDateTime.now())
                .updatedTime(LocalDateTime.now())
                .build();

        int result = questionDOMapper.insertSelective(questionDO);
        if (result <= 0) {
            log.error("创建题目失败，用户ID: {}, 题目ID: {}", currentUserId, questionId);
            throw new BizException(ResponseCodeEnum.QUESTION_CREATE_FAILED);
        }

        // 创建成功，构建VO返回
        QuestionVO questionVO = QuestionVO.builder()
                .id(questionDO.getId())
                .content(questionDO.getContent())
                .answer(questionDO.getAnswer())
                .analysis(questionDO.getAnalysis())
                .processStatus(questionDO.getProcessStatus())
                .createdTime(questionDO.getCreatedTime())
                .updatedTime(questionDO.getUpdatedTime())
                .createdBy(questionDO.getCreatedBy())
                .build();

        log.info("创建题目成功，用户ID: {}, 创建的题目ID: {}", currentUserId, questionDO.getId());
        return Response.success(questionVO);
    }

    /**
     * 分页查询
     *
     * @param request
     * @return
     */
    @Override
    public Response<?> pageQuestions(QuestionPageQueryDTO request) {
        log.info("开始分页查询题目，请求参数: {}", request);
        PageHelper.startPage(request.getPage(), request.getPageSize());
        QuestionDO questionDO = new QuestionDO();

        BeanUtils.copyProperties(request, questionDO);
        //设置id
        Long currentUserId = requiredUserId();
        questionDO.setCreatedBy(currentUserId);


        List<QuestionDO> questionDOList = questionDOMapper.selectByExample(questionDO);
        // 转换为VO列表
        List<QuestionVO> questionVOList = questionDOList.stream()
                .map(question -> QuestionVO.builder()
                        .id(question.getId())
                        .content(question.getContent())
                        .answer(question.getAnswer())
                        .analysis(question.getAnalysis())
                        .processStatus(question.getProcessStatus())
                        .createdTime(question.getCreatedTime())
                        .updatedTime(question.getUpdatedTime())
                        .createdBy(question.getCreatedBy())
                        .build())
                .collect(Collectors.toList());

        // 封装分页结果
        PageInfo<QuestionVO> pageInfo = new PageInfo<>(questionVOList);
        return Response.success(pageInfo);


    }

    /**
     * 根据id删除题目
     *
     * @param ids 题目ID列表
     * @return 删除结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<?> deleteQuestions(List<Long> ids) {
        log.info("开始删除题目，请求参数: {}", ids);

        // 参数校验
        if (ids == null || ids.isEmpty()) {
            log.warn("删除题目参数为空");
            return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), "删除ID列表不能为空");
        }

        try {
            // 获取当前用户ID
            Long currentUserId = requiredUserId();

            // 查询待删除的题目，验证权限（确保只能删除自己创建的题目）
            QuestionDO queryCondition = new QuestionDO();
            queryCondition.setCreatedBy(currentUserId);
            List<QuestionDO> questionsToDelete = questionDOMapper.selectByExample(queryCondition);

            // 提取当前用户有权限操作的题目。
            List<QuestionDO> authorizedQuestions = questionsToDelete.stream()
                    .filter(question -> ids.contains(question.getId()))
                    .collect(Collectors.toList());

            if (authorizedQuestions.isEmpty()) {
                log.warn("没有权限删除指定的题目或题目不存在");
                return Response.success("未删除任何题目（无权限或题目不存在）");
            }

            List<Long> deletableIds = authorizedQuestions.stream()
                    .filter(question -> isQuestionMutable(question.getProcessStatus()))
                    .map(QuestionDO::getId)
                    .collect(Collectors.toList());

            int skippedByStatusCount = authorizedQuestions.size() - deletableIds.size();
            if (deletableIds.isEmpty()) {
                log.warn("删除被状态约束拦截，用户ID: {}, ids={}", currentUserId, ids);
                return Response.fail(ResponseCodeEnum.QUESTION_STATUS_NOT_ALLOWED.getErrorCode(),
                        "仅允许删除 WAITING 或 PROCESS_FAILED 状态的题目");
            }

            int deletedCount = questionDOMapper.deleteBatchByCreatorAndStatuses(deletableIds, currentUserId, MUTABLE_STATUSES);
            int skippedByRaceCount = deletableIds.size() - deletedCount;
            int totalSkippedCount = skippedByStatusCount + skippedByRaceCount;

            log.info("删除题目成功，用户ID: {}, 删除数量: {}, 状态拦截数量: {}",
                    currentUserId, deletedCount, totalSkippedCount);

            if (deletedCount <= 0) {
                return Response.success("未删除任何题目（状态已变化或当前状态不允许删除）");
            }

            if (totalSkippedCount > 0) {
                String message = skippedByRaceCount > 0
                        ? String.format("已删除 %d 条，跳过 %d 条（状态已变化或仅允许删除 WAITING/PROCESS_FAILED 状态题目）",
                        deletedCount, totalSkippedCount)
                        : String.format("已删除 %d 条，跳过 %d 条（仅允许删除 WAITING/PROCESS_FAILED 状态题目）",
                        deletedCount, totalSkippedCount);
                return Response.success(message);
            }

            return Response.success();

        } catch (Exception e) {
            log.error("删除题目失败，参数: {}", ids, e);
            throw new BizException(ResponseCodeEnum.QUESTION_DELETE_FAILED);
        }
    }

    @Override
    public Response<QuestionVO> updateQuestion(Long id, UpdateQuestionDTO request) {
        log.info("开始更新题目，题目ID: {}, 请求参数: {}", id, request);
        
        // 参数校验
        if (id == null) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }
        
        // 获取当前用户ID
        Long currentUserId = requiredUserId();
        
        // 查询题目是否存在并验证权限
        QuestionDO existingQuestion = questionDOMapper.selectByPrimaryKey(id);
        if (existingQuestion == null) {
            log.warn("题目不存在，ID: {}", id);
            throw new BizException(ResponseCodeEnum.QUESTION_NOT_FOUND);
        }
        
        // 验证是否是当前用户创建的题目
        if (!existingQuestion.getCreatedBy().equals(currentUserId)) {
            log.warn("无权限更新题目，用户ID: {}, 题目ID: {}", currentUserId, id);
            throw new BizException(ResponseCodeEnum.NO_PERMISSION);
        }

        if (!isQuestionMutable(existingQuestion.getProcessStatus())) {
            log.warn("题目状态不允许更新，用户ID: {}, 题目ID: {}, status={}",
                    currentUserId, id, existingQuestion.getProcessStatus());
            throw new BizException(ResponseCodeEnum.QUESTION_STATUS_NOT_ALLOWED.getErrorCode(),
                    "仅允许编辑 WAITING 或 PROCESS_FAILED 状态的题目");
        }
        
        // 创建更新对象
        QuestionDO updateDO = new QuestionDO();
        updateDO.setId(id);
        updateDO.setContent(request.getContent());
        updateDO.setAnswer(request.getAnswer());
        updateDO.setAnalysis(request.getAnalysis());
        updateDO.setUpdatedTime(LocalDateTime.now()); // 后端自动设置更新时间
        
        // 执行更新
        int result = questionDOMapper.updateEditableQuestion(updateDO, currentUserId, existingQuestion.getProcessStatus());
        if (result <= 0) {
            log.warn("更新题目失败，题目状态已变化，用户ID: {}, 题目ID: {}, expectedStatus={}",
                    currentUserId, id, existingQuestion.getProcessStatus());
            throw new BizException(ResponseCodeEnum.QUESTION_UPDATE_FAILED.getErrorCode(), "题目状态已变化，请刷新后重试");
        }
        
        // 查询更新后的题目信息
        QuestionDO updatedQuestion = questionDOMapper.selectByPrimaryKey(id);
        
        // 构建VO返回
        QuestionVO questionVO = QuestionVO.builder()
                .id(updatedQuestion.getId())
                .content(updatedQuestion.getContent())
                .answer(updatedQuestion.getAnswer())
                .analysis(updatedQuestion.getAnalysis())
                .processStatus(updatedQuestion.getProcessStatus())
                .createdTime(updatedQuestion.getCreatedTime())
                .updatedTime(updatedQuestion.getUpdatedTime())
                .createdBy(updatedQuestion.getCreatedBy())
                .build();
        
        log.info("更新题目成功，用户ID: {}, 题目ID: {}", currentUserId, id);
        return Response.success(questionVO);
    }

    @Override
    public Response<?> reviewQuestion(Long id, ReviewQuestionRequestDTO request) {
        log.info("开始审核题目，题目ID: {}, 请求参数: {}", id, request);

        if (id == null || request == null || StringUtils.isBlank(request.getAction())) {
            return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), "审核参数错误");
        }

        String normalizedAction = request.getAction().trim().toUpperCase();
        if (!"APPROVE".equals(normalizedAction) && !"REJECT".equals(normalizedAction)) {
            return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), "action 仅支持 APPROVE 或 REJECT");
        }

        Long currentUserId = requiredUserId();

        QuestionDO current = questionDOMapper.selectByPrimaryKey(id);
        if (current == null) {
            throw new BizException(ResponseCodeEnum.QUESTION_NOT_FOUND);
        }
        if (!currentUserId.equals(current.getCreatedBy())) {
            throw new BizException(ResponseCodeEnum.NO_PERMISSION);
        }

        QuestionStatusActionEnum action = "APPROVE".equals(normalizedAction)
                ? QuestionStatusActionEnum.APPROVE
                : QuestionStatusActionEnum.REJECT;
        String currentStatus = resolvedStatusCode(current.getProcessStatus());
        String nextStatus = nextStatusCodeOrNull(currentStatus, action);
        if (nextStatus == null) {
            return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(),
                    "当前状态不允许执行 " + normalizedAction);
        }

        int updatedRows;
        if (QuestionStatusActionEnum.REJECT.equals(action) && shouldClearAnswerOnReject(request, current)) {
            updatedRows = questionDOMapper.transitStatusAndClearAnswerByExpectedStatus(id, currentStatus, nextStatus);
        } else {
            updatedRows = questionDOMapper.transitStatus(id, currentStatus, nextStatus);
        }

        if (updatedRows <= 0) {
            return Response.fail(ResponseCodeEnum.QUESTION_UPDATE_FAILED.getErrorCode(),
                    "题目状态已变化，请刷新后重试");
        }
        return Response.success();
    }

    private Long requiredUserId() {
        Long userId;
        try {
            userId = LoginUserContextHolder.getUserId();
        } catch (NumberFormatException e) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), "请求头 userId 必须是数字");
        }
        if (userId == null) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), "请求头 userId 不能为空");
        }
        return userId;
    }

    private boolean shouldClearAnswerOnReject(ReviewQuestionRequestDTO request, QuestionDO current) {
        if (Boolean.TRUE.equals(request.getClearAnswerOnReject())) {
            return true;
        }
        // 本地 mock 生成答案默认允许驳回时清空，便于联调恢复“无答案”状态。
        return current != null && StringUtils.startsWith(StringUtils.defaultString(current.getAnswer()), "【MOCK-AI】");
    }

    private boolean hasAnswer(QuestionDO question) {
        return question != null && StringUtils.isNotBlank(question.getAnswer());
    }

    private boolean isQuestionMutable(String processStatus) {
        String status = resolvedStatusCode(processStatus);
        return QuestionProcessStatusEnum.WAITING.getCode().equals(status)
                || QuestionProcessStatusEnum.PROCESS_FAILED.getCode().equals(status);
    }

    private boolean shouldOverwriteAnswer(AIProcessResultMessage aiResult, QuestionDO current) {
        if (aiResult == null) {
            return false;
        }

        String mode = StringUtils.upperCase(StringUtils.trimToNull(aiResult.getMode()));
        if (MODE_GENERATE.equals(mode)) {
            return true;
        }
        if (MODE_VALIDATE.equals(mode)) {
            return false;
        }

        // 兼容旧回包：无 mode 时，若存在明确校验结论或原题已有答案，则视为 VALIDATE。
        String validationResult = StringUtils.upperCase(StringUtils.trimToNull(aiResult.getValidationResult()));
        if (StringUtils.isNotBlank(validationResult) && !"NA".equals(validationResult)) {
            return false;
        }
        return current == null || !hasAnswer(current);
    }

    @Override
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
                String questionIdStr = entry.getKey();
                Long questionId = Long.valueOf(questionIdStr);
                AIProcessResultMessage aiResult = entry.getValue();

                QuestionDO current = questionDOMapper.selectByPrimaryKey(questionId);
                if (current == null) {
                    log.warn("单条更新跳过，题目不存在，题目ID: {}", questionId);
                    continue;
                }
                String currentStatus = resolvedStatusCode(current.getProcessStatus());
                String nextStatus = nextStatusCodeOrNull(currentStatus, QuestionStatusActionEnum.AI_SUCCESS);
                if (nextStatus == null) {
                    log.warn("单条更新跳过，非法流转，题目ID: {}, currentStatus={}, action={}",
                            questionId, currentStatus, QuestionStatusActionEnum.AI_SUCCESS);
                    continue;
                }

                String resolvedAiAnswer = aiResult != null ? aiResult.resolvedAiAnswer() : null;
                boolean shouldOverwrite = StringUtils.isNotBlank(resolvedAiAnswer) && shouldOverwriteAnswer(aiResult, current);
                int updatedRows = shouldOverwrite
                        ? questionDOMapper.transitStatusAndAnswer(questionId, currentStatus, nextStatus, resolvedAiAnswer)
                        : questionDOMapper.transitStatus(questionId, currentStatus, nextStatus);
                if (updatedRows > 0) {
                    updateCount++;
                    continue;
                }
                log.warn("单条更新跳过，题目状态已变化，题目ID: {}, expectedStatus={}", questionId, currentStatus);
            } catch (NumberFormatException e) {
                log.error("题目ID格式错误: {}", entry.getKey(), e);
            } catch (Exception e) {
                log.error("单条更新失败，题目ID: {}", entry.getKey(), e);
            }
        }
        log.info("批量更新成功题目完成，计划更新: {}, 实际更新: {}", successResults.size(), updateCount);
        return updateCount;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchUpdateFailedQuestions(Map<String, String> errorResults) {
        if (errorResults == null || errorResults.isEmpty()) {
            log.info("没有需要批量更新的失败题目");
            return 0;
        }
        
        log.info("开始批量更新失败题目状态，待处理数量: {}", errorResults.size());
        int updateCount = 0;

        for (Map.Entry<String, String> entry : errorResults.entrySet()) {
            try {
                String questionIdStr = entry.getKey();
                Long questionId = Long.valueOf(questionIdStr);

                QuestionDO current = questionDOMapper.selectByPrimaryKey(questionId);
                if (current == null) {
                    log.warn("单条更新跳过，题目不存在，题目ID: {}", questionId);
                    continue;
                }
                String currentStatus = resolvedStatusCode(current.getProcessStatus());
                String nextStatus = nextStatusCodeOrNull(currentStatus, QuestionStatusActionEnum.AI_FAIL);
                if (nextStatus == null) {
                    log.warn("单条更新跳过，非法流转，题目ID: {}, currentStatus={}, action={}",
                            questionId, currentStatus, QuestionStatusActionEnum.AI_FAIL);
                    continue;
                }
                if (questionDOMapper.transitStatus(questionId, currentStatus, nextStatus) > 0) {
                    updateCount++;
                    continue;
                }
                log.warn("单条更新跳过，题目状态已变化，题目ID: {}, expectedStatus={}", questionId, currentStatus);
            } catch (NumberFormatException e) {
                log.error("题目ID格式错误: {}", entry.getKey(), e);
            } catch (Exception e) {
                log.error("单条更新失败，题目ID: {}", entry.getKey(), e);
            }
        }
        log.info("批量更新失败题目完成，计划更新: {}, 实际更新: {}", errorResults.size(), updateCount);
        return updateCount;
    }

    private boolean markQuestionProcessing(QuestionDO question) {
        if (question == null || question.getId() == null) {
            return false;
        }
        return questionDOMapper.transitStatus(question.getId(),
                resolvedStatusCode(question.getProcessStatus()),
                QuestionProcessStatusEnum.PROCESSING.getCode()) > 0;
    }

    private void rollbackProcessingQuestion(Long questionId) {
        if (questionId == null) {
            return;
        }
        int rollbackRows = questionDOMapper.transitStatus(questionId,
                QuestionProcessStatusEnum.PROCESSING.getCode(),
                QuestionProcessStatusEnum.WAITING.getCode());
        if (rollbackRows <= 0) {
            log.warn("发送失败后的状态回滚未生效，questionId={}", questionId);
        }
    }

    private String resolvedStatusCode(String currentStatus) {
        return QuestionProcessStatusEnum.from(currentStatus)
                .map(QuestionProcessStatusEnum::getCode)
                .orElse(currentStatus);
    }
}

