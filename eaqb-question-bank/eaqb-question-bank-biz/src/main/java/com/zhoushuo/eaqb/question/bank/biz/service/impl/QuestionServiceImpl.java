package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.rpc.DistributedIdGeneratorRpcService;
import com.zhoushuo.eaqb.question.bank.biz.service.QuestionService;
import com.zhoushuo.eaqb.question.bank.req.BatchImportQuestionRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.QuestionDTO;
import com.zhoushuo.eaqb.question.bank.resp.BatchImportQuestionResponseDTO;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
}

