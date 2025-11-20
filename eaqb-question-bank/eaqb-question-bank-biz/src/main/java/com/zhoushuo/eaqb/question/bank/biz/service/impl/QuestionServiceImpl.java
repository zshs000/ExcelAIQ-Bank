package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.zhoushuo.eaqb.question.bank.biz.constant.MQConstants;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.CreateQuestionDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.QuestionPageQueryDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.UpdateQuestionDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.vo.QuestionVO;
import com.zhoushuo.eaqb.question.bank.biz.rpc.DistributedIdGeneratorRpcService;
import com.zhoushuo.eaqb.question.bank.biz.service.QuestionService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.zhoushuo.eaqb.question.bank.biz.model.QuestionMessage;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

@Slf4j
@Service
public class QuestionServiceImpl implements QuestionService {
    
    // 现有注入
    @Autowired
    private QuestionDOMapper questionDOMapper;
    
    @Autowired
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    
    // 添加RocketMQTemplate注入
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    

    
    // 实现发送消息到队列的方法
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<?> sendQuestionsToQueue(List<Long> questionIds) {
        log.info("开始批量发送题目到消息队列，题目数量: {}", questionIds.size());
        
        // 参数校验
        if (questionIds == null || questionIds.isEmpty()) {
            return Response.fail("题目ID列表不能为空");
        }
        
        try {
            // 1. 批量更新题目状态为PROCESSING
            int updateCount = questionDOMapper.updateBatchStatus(questionIds, "PROCESSING");
            log.info("更新题目状态成功，更新数量: {}", updateCount);
            
            // 2. 批量查询题目信息
            List<QuestionDO> questions = questionDOMapper.selectBatchByIds(questionIds);
            log.info("查询题目信息成功，查询数量: {}", questions.size());
            
            // 3. 发送题目到消息队列
            int sendSuccessCount = 0;
            for (QuestionDO question : questions) {
                try {
                    // 构建消息对象
                    QuestionMessage message = new QuestionMessage(
                            String.valueOf(question.getId()),
                            question.getContent()
                    );
                    
                    // 创建Message对象
                    Message<QuestionMessage> msg = MessageBuilder.withPayload(message).build();
                    log.info("发送消息对象: {}", msg);
                    
                    // 发送消息到队列，不指定消息ID，使用默认生成的
                    rocketMQTemplate.syncSend(MQConstants.TOPIC_TEST, msg);
                    
                    sendSuccessCount++;
                    log.info("题目ID: {} 发送到队列成功", question.getId());
                } catch (Exception e) {
                    log.error("题目ID: {} 发送到队列失败", question.getId(), e);
                    // 可以选择抛出异常或继续处理下一个题目
                    throw new BizException(ResponseCodeEnum.QUESTION_SEND_FAILED);
                }
            }
            return Response.success(
                    String.format("题目发送成功，共发送 %d 道题目，成功 %d 道", 
                            questions.size(), sendSuccessCount)
            );
            
        } catch (Exception e) {
            log.error("批量发送题目到消息队列失败", e);
            throw new BizException(ResponseCodeEnum.QUESTION_SEND_FAILED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<BatchImportQuestionResponseDTO> batchImportQuestions(BatchImportQuestionRequestDTO request) {
        log.info("开始批量导入题目，请求数量: {}", request != null ? request.getQuestions().size() : 0);

        // 参数空检查（基础防御）
        if (request == null || request.getQuestions() == null || request.getQuestions().isEmpty()) {
            log.warn("批量导入题目参数为空");
            BatchImportQuestionResponseDTO response = BatchImportQuestionResponseDTO.builder()
                    .success(false)
                    .errorMessage("导入题目列表不能为空")
                    .errorType("SYSTEM_ERROR")
                    .totalCount(0)
                    .successCount(0)
                    .failedCount(0)
                    .build();
            return Response.success(response);
        }

        Long currentUserId = LoginUserContextHolder.getUserId();
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
                        .processStatus("WAITING")
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

            BatchImportQuestionResponseDTO response = BatchImportQuestionResponseDTO.builder()
                    .success(false)
                    .errorMessage(errorMsg)
                    .errorType("SYSTEM_ERROR")
                    .totalCount(totalCount)
                    .successCount(0)
                    .failedCount(totalCount)
                    .build();

            return Response.success(response);
        }
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
        Long currentUserId = LoginUserContextHolder.getUserId();

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
                .processStatus("WAITING")
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

        BeanUtils.copyProperties(questionDO, request);
        //设置id
        Long currentUserId = LoginUserContextHolder.getUserId();
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
            return Response.success();
        }

        try {
            // 获取当前用户ID
            Long currentUserId = LoginUserContextHolder.getUserId();

            // 查询待删除的题目，验证权限（确保只能删除自己创建的题目）
            QuestionDO queryCondition = new QuestionDO();
            queryCondition.setCreatedBy(currentUserId);
            List<QuestionDO> questionsToDelete = questionDOMapper.selectByExample(queryCondition);

            // 提取有权限删除的题目ID
            List<Long> authorizedIds = questionsToDelete.stream()
                    .map(QuestionDO::getId)
                    .filter(ids::contains)
                    .collect(Collectors.toList());

            if (authorizedIds.isEmpty()) {
                log.warn("没有权限删除指定的题目或题目不存在");
                return Response.success("未删除任何题目（无权限或题目不存在）");
            }

            // 批量删除题目
            int deletedCount = questionDOMapper.deleteBatch(authorizedIds);

            log.info("删除题目成功，用户ID: {}, 删除数量: {}", currentUserId, deletedCount);
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
        Long currentUserId = LoginUserContextHolder.getUserId();
        
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
        
        // 创建更新对象
        QuestionDO updateDO = new QuestionDO();
        updateDO.setId(id);
        updateDO.setContent(request.getContent());
        updateDO.setAnswer(request.getAnswer());
        updateDO.setAnalysis(request.getAnalysis());
        updateDO.setUpdatedTime(LocalDateTime.now()); // 后端自动设置更新时间
        
        // 执行更新
        int result = questionDOMapper.updateByPrimaryKeySelective(updateDO);
        if (result <= 0) {
            log.error("更新题目失败，用户ID: {}, 题目ID: {}", currentUserId, id);
            throw new BizException(ResponseCodeEnum.QUESTION_UPDATE_FAILED);
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
    
    // 在现有实现类中添加方法实现
    @Override
    public void updateQuestionStatusToReview(String questionId, String answer) {
        // 1. 查询题目是否存在
        QuestionDO questionDO = questionDOMapper.selectByPrimaryKey(Long.valueOf(questionId));
        if (questionDO == null) {
            log.warn("更新题目状态失败：题目不存在，questionId={}", questionId);
            return;
        }
        
        // 2. 更新题目状态为待审查，并保存AI生成的答案
        QuestionDO updateDO = new QuestionDO();
        updateDO.setId(Long.valueOf(questionId));
        updateDO.setProcessStatus("REVIEW_PENDING"); // 假设待审查状态值为REVIEW_PENDING
        updateDO.setAnswer(answer);
        updateDO.setUpdatedTime(LocalDateTime.now());
        
        // 3. 执行更新
        int result = questionDOMapper.updateByPrimaryKey(updateDO);
        if (result > 0) {
            log.info("题目状态更新成功：questionId={}, 状态更新为待审查", questionId);
        } else {
            log.error("题目状态更新失败：questionId={}", questionId);
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchUpdateSuccessQuestions(Map<String, String> successResults) {
        if (successResults == null || successResults.isEmpty()) {
            log.info("没有需要批量更新的成功题目");
            return 0;
        }
        
        log.info("开始批量更新成功题目状态，待处理数量: {}", successResults.size());
        int updateCount = 0;
        
        // 批量创建更新对象
        List<QuestionDO> updateList = new ArrayList<>(successResults.size());
        LocalDateTime now = LocalDateTime.now();
        
        for (Map.Entry<String, String> entry : successResults.entrySet()) {
            try {
                String questionIdStr = entry.getKey();
                String answer = entry.getValue();
                Long questionId = Long.valueOf(questionIdStr);
                
                // 创建更新对象
                QuestionDO updateDO = new QuestionDO();
                updateDO.setId(questionId);
                updateDO.setProcessStatus("REVIEW_PENDING");
                updateDO.setAnswer(answer);
                updateDO.setUpdatedTime(now);
                
                updateList.add(updateDO);
            } catch (NumberFormatException e) {
                log.error("题目ID格式错误: {}", entry.getKey(), e);
            }
        }
        
        // 执行批量更新
        if (!updateList.isEmpty()) {
            // 检查是否支持批量更新操作
            try {
                // 这里假设mapper支持批量更新，如果不支持则需要循环调用单条更新
                // 实际实现需要根据数据库访问层的具体实现来调整
                updateCount = questionDOMapper.updateBatch(updateList);
                log.info("批量更新成功题目完成，计划更新: {}, 实际更新: {}", updateList.size(), updateCount);
            } catch (Exception e) {
                log.error("批量更新成功题目异常，尝试逐条更新", e);
                // 如果批量更新失败，尝试逐条更新
                for (QuestionDO updateDO : updateList) {
                    try {
                        if (questionDOMapper.updateByPrimaryKey(updateDO) > 0) {
                            updateCount++;
                        }
                    } catch (Exception ex) {
                        log.error("单条更新失败，题目ID: {}", updateDO.getId(), ex);
                    }
                }
                log.info("逐条更新完成，成功更新: {}", updateCount);
            }
        }
        
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
        
        // 批量创建更新对象
        List<QuestionDO> updateList = new ArrayList<>(errorResults.size());
        LocalDateTime now = LocalDateTime.now();
        
        for (Map.Entry<String, String> entry : errorResults.entrySet()) {
            try {
                String questionIdStr = entry.getKey();
                String errorMessage = entry.getValue();
                Long questionId = Long.valueOf(questionIdStr);
                
                // 创建更新对象
                QuestionDO updateDO = new QuestionDO();
                updateDO.setId(questionId);
                updateDO.setProcessStatus("PROCESS_FAILED"); // 失败状态
                updateDO.setErrorMessage(errorMessage); // 假设QuestionDO有errorMessage字段
                updateDO.setUpdatedTime(now);
                
                updateList.add(updateDO);
            } catch (NumberFormatException e) {
                log.error("题目ID格式错误: {}", entry.getKey(), e);
            }
        }
        
        // 执行批量更新
        if (!updateList.isEmpty()) {
            try {
                // 尝试批量更新
                updateCount = questionDOMapper.updateBatch(updateList);
                log.info("批量更新失败题目完成，计划更新: {}, 实际更新: {}", updateList.size(), updateCount);
            } catch (Exception e) {
                log.error("批量更新失败题目异常，尝试逐条更新", e);
                // 如果批量更新失败，尝试逐条更新
                for (QuestionDO updateDO : updateList) {
                    try {
                        if (questionDOMapper.updateByPrimaryKey(updateDO) > 0) {
                            updateCount++;
                        }
                    } catch (Exception ex) {
                        log.error("单条更新失败，题目ID: {}", updateDO.getId(), ex);
                    }
                }
                log.info("逐条更新完成，成功更新: {}", updateCount);
            }
        }
        
        return updateCount;
    }
}

