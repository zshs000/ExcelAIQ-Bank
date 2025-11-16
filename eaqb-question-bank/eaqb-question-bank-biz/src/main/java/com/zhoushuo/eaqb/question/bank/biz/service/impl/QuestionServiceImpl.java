package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
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

@Slf4j
@Service
public class QuestionServiceImpl implements QuestionService {

    @Autowired
    private QuestionDOMapper questionDOMapper;

    @Autowired
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;

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
}

