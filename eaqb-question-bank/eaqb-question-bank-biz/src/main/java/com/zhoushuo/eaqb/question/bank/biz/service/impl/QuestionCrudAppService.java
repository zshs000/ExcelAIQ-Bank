package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionProcessStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.CreateQuestionDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.QuestionPageQueryDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.UpdateQuestionDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.vo.QuestionVO;
import com.zhoushuo.eaqb.question.bank.biz.rpc.DistributedIdGeneratorRpcService;
import com.zhoushuo.eaqb.question.bank.req.BatchImportQuestionRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.QuestionDTO;
import com.zhoushuo.eaqb.question.bank.resp.BatchImportQuestionResponseDTO;
import com.zhoushuo.framework.common.exception.BizException;
import com.zhoushuo.framework.common.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class QuestionCrudAppService {

    private static final List<String> MUTABLE_STATUSES = List.of(
            QuestionProcessStatusEnum.WAITING.getCode(),
            QuestionProcessStatusEnum.PROCESS_FAILED.getCode()
    );

    @Autowired
    private QuestionDOMapper questionDOMapper;

    @Autowired
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;

    @Autowired
    private QuestionAccessSupport questionAccessSupport;

    /**
     * 批量导入题目。
     *
     * <p>这里有两层返回语义：</p>
     *
     * <ul>
     *   <li>外层 {@link Response}：表示这次 RPC / HTTP 调用是否成功拿到一个合法的“导入结果”</li>
     *   <li>内层 {@link BatchImportQuestionResponseDTO#isSuccess()}：表示导入业务本身成功还是失败</li>
     * </ul>
     *
     * <p>因此边界被约定为：</p>
     *
     * <ul>
     *   <li>请求参数不合法、权限不满足等“尚未进入导入执行”的情况：返回 {@code Response.fail(...)}</li>
     *   <li>一旦请求已经进入导入执行阶段，就尽量返回 {@code Response.success(dto)}</li>
     *   <li>导入执行结果再由 dto 内部的 {@code success / failedCount / errorMessage / errorType} 表达</li>
     * </ul>
     */
    @Transactional(rollbackFor = Exception.class)
    public Response<BatchImportQuestionResponseDTO> batchImportQuestions(BatchImportQuestionRequestDTO request) {
        log.info("开始批量导入题目，请求数量: {}", request != null ? request.getQuestions().size() : 0);

        if (request == null || request.getQuestions() == null || request.getQuestions().isEmpty()) {
            log.warn("批量导入题目参数为空");
            return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), "导入题目列表不能为空");
        }

        Long currentUserId = questionAccessSupport.requireCurrentUserId();
        int totalCount = request.getQuestions().size();
        log.info("当前用户ID: {}, 导入题目数量: {}", currentUserId, totalCount);

        try {
            List<QuestionDO> questionDOList = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            for (QuestionDTO dto : request.getQuestions()) {
                Long questionId = Long.valueOf(distributedIdGeneratorRpcService.nextQuestionBankEntityId());
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

            int successCount = questionDOMapper.batchInsert(questionDOList);
            if (successCount != totalCount) {
                log.warn("批量导入题目数量不一致，用户ID: {}, 期望数量: {}, 实际数量: {}",
                        currentUserId, totalCount, successCount);
                return Response.success(buildBatchImportFailureResult(
                        totalCount,
                        successCount,
                        "导入题目数量不一致，请稍后重试",
                        ResponseCodeEnum.QUESTION_CREATE_FAILED.getErrorCode()
                ));
            }

            log.info("批量导入题目成功，用户ID: {}, 成功数量: {}", currentUserId, successCount);
            return Response.success(BatchImportQuestionResponseDTO.builder()
                    .success(true)
                    .totalCount(totalCount)
                    .successCount(successCount)
                    .failedCount(0)
                    .build());
        } catch (Exception e) {
            log.error("批量导入题目系统错误，用户ID: {}", currentUserId, e);
            String errorMsg = "导入失败，请稍后重试";
            if (e instanceof RuntimeException && StringUtils.isNotBlank(e.getMessage())) {
                errorMsg = e.getMessage();
            } else if (e instanceof DataIntegrityViolationException) {
                errorMsg = "数据库约束冲突，请检查数据";
            } else if (e instanceof SQLException) {
                errorMsg = "数据库操作失败，请稍后重试";
            }
            return Response.success(buildBatchImportFailureResult(
                    totalCount,
                    0,
                    errorMsg,
                    ResponseCodeEnum.SYSTEM_ERROR.getErrorCode()
            ));
        }
    }

    /**
     * 构造“调用成功拿到了导入结果，但导入业务本身失败”的结果对象。
     *
     * <p>注意这不是外层 {@code Response.fail(...)}，而是 provider 明确返回的业务失败摘要。</p>
     */
    private BatchImportQuestionResponseDTO buildBatchImportFailureResult(int totalCount,
                                                                         int successCount,
                                                                         String errorMessage,
                                                                         String errorType) {
        int normalizedSuccessCount = Math.max(0, Math.min(successCount, totalCount));
        return BatchImportQuestionResponseDTO.builder()
                .success(false)
                .totalCount(totalCount)
                .successCount(normalizedSuccessCount)
                .failedCount(totalCount - normalizedSuccessCount)
                .errorMessage(errorMessage)
                .errorType(errorType)
                .build();
    }

    public Response<QuestionVO> createQuestion(CreateQuestionDTO request) {
        log.info("开始创建题目，请求参数: {}", request);
        Long currentUserId = questionAccessSupport.requireCurrentUserId();

        Long questionId = Long.valueOf(distributedIdGeneratorRpcService.nextQuestionBankEntityId());
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

        if (questionDOMapper.insertSelective(questionDO) <= 0) {
            log.error("创建题目失败，用户ID: {}, 题目ID: {}", currentUserId, questionId);
            throw new BizException(ResponseCodeEnum.QUESTION_CREATE_FAILED);
        }

        log.info("创建题目成功，用户ID: {}, 创建的题目ID: {}", currentUserId, questionDO.getId());
        return Response.success(toQuestionVO(questionDO));
    }

    public Response<QuestionVO> getQuestionById(Long id) {
        log.info("开始查询题目详情，题目ID: {}", id);
        if (id == null) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }

        Long currentUserId = questionAccessSupport.requireCurrentUserId();
        QuestionDO questionDO = questionDOMapper.selectByPrimaryKey(id);
        if (questionDO == null) {
            log.warn("题目不存在，ID: {}", id);
            throw new BizException(ResponseCodeEnum.QUESTION_NOT_FOUND);
        }
        if (!currentUserId.equals(questionDO.getCreatedBy())) {
            log.warn("无权限查看题目详情，用户ID: {}, 题目ID: {}", currentUserId, id);
            throw new BizException(ResponseCodeEnum.NO_PERMISSION);
        }
        return Response.success(toQuestionVO(questionDO));
    }

    public Response<?> pageQuestions(QuestionPageQueryDTO request) {
        log.info("开始分页查询题目，请求参数: {}", request);
        PageHelper.startPage(request.getPage(), request.getPageSize());

        QuestionDO query = new QuestionDO();
        BeanUtils.copyProperties(request, query);
        query.setCreatedBy(questionAccessSupport.requireCurrentUserId());

        List<QuestionVO> questionVOList = questionDOMapper.selectByExample(query).stream()
                .map(this::toQuestionVO)
                .collect(Collectors.toList());
        return Response.success(new PageInfo<>(questionVOList));
    }

    @Transactional(rollbackFor = Exception.class)
    public Response<?> deleteQuestions(List<Long> ids) {
        log.info("开始删除题目，请求参数: {}", ids);
        if (ids == null || ids.isEmpty()) {
            log.warn("删除题目参数为空");
            return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), "删除ID列表不能为空");
        }

        try {
            Long currentUserId = questionAccessSupport.requireCurrentUserId();
            QuestionDO queryCondition = new QuestionDO();
            queryCondition.setCreatedBy(currentUserId);
            List<QuestionDO> authorizedQuestions = questionDOMapper.selectByExample(queryCondition).stream()
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

    public Response<QuestionVO> updateQuestion(Long id, UpdateQuestionDTO request) {
        log.info("开始更新题目，题目ID: {}, 请求参数: {}", id, request);
        if (id == null) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }

        Long currentUserId = questionAccessSupport.requireCurrentUserId();
        QuestionDO existingQuestion = questionDOMapper.selectByPrimaryKey(id);
        if (existingQuestion == null) {
            log.warn("题目不存在，ID: {}", id);
            throw new BizException(ResponseCodeEnum.QUESTION_NOT_FOUND);
        }
        if (!existingQuestion.getCreatedBy().equals(currentUserId)) {
            log.warn("无权限更新题目，用户ID: {}, 题目ID: {}", currentUserId, id);
            throw new BizException(ResponseCodeEnum.NO_PERMISSION);
        }
        if (!isQuestionMutable(existingQuestion.getProcessStatus())) {
            log.warn("题目状态不允许更新，用户ID: {}, 题目ID: {}, status={}",
                    currentUserId, id, existingQuestion.getProcessStatus());
            throw bizException(ResponseCodeEnum.QUESTION_STATUS_NOT_ALLOWED.getErrorCode(),
                    "仅允许编辑 WAITING 或 PROCESS_FAILED 状态的题目");
        }

        QuestionDO updateDO = new QuestionDO();
        updateDO.setId(id);
        updateDO.setContent(request.getContent());
        updateDO.setAnswer(request.getAnswer());
        updateDO.setAnalysis(request.getAnalysis());
        updateDO.setUpdatedTime(LocalDateTime.now());

        int result = questionDOMapper.updateEditableQuestion(updateDO, currentUserId, existingQuestion.getProcessStatus());
        if (result <= 0) {
            log.warn("更新题目失败，题目状态已变化，用户ID: {}, 题目ID: {}, expectedStatus={}",
                    currentUserId, id, existingQuestion.getProcessStatus());
            throw bizException(ResponseCodeEnum.QUESTION_UPDATE_FAILED.getErrorCode(), "题目状态已变化，请刷新后重试");
        }

        QuestionDO updatedQuestion = questionDOMapper.selectByPrimaryKey(id);
        log.info("更新题目成功，用户ID: {}, 题目ID: {}", currentUserId, id);
        return Response.success(toQuestionVO(updatedQuestion));
    }

    private QuestionVO toQuestionVO(QuestionDO questionDO) {
        return QuestionVO.builder()
                .id(questionDO.getId())
                .content(questionDO.getContent())
                .answer(questionDO.getAnswer())
                .analysis(questionDO.getAnalysis())
                .processStatus(questionDO.getProcessStatus())
                .createdTime(questionDO.getCreatedTime())
                .updatedTime(questionDO.getUpdatedTime())
                .createdBy(questionDO.getCreatedBy())
                .build();
    }

    private boolean isQuestionMutable(String processStatus) {
        String status = QuestionProcessStatusEnum.from(processStatus)
                .map(QuestionProcessStatusEnum::getCode)
                .orElse(processStatus);
        return QuestionProcessStatusEnum.WAITING.getCode().equals(status)
                || QuestionProcessStatusEnum.PROCESS_FAILED.getCode().equals(status);
    }

    private BizException bizException(String errorCode, String errorMessage) {
        return new BizException(errorCode, errorMessage);
    }
}
