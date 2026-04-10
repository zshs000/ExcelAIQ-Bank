package com.zhoushuo.eaqb.question.bank.biz.service;

import com.zhoushuo.eaqb.question.bank.biz.model.dto.CreateQuestionDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.QuestionPageQueryDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.ReviewQuestionRequestDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.UpdateQuestionDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.vo.QuestionVO;
import com.zhoushuo.eaqb.question.bank.biz.model.AIProcessResultMessage;
import com.zhoushuo.eaqb.question.bank.req.AppendImportChunkRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.BatchImportQuestionRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.CommitImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.CreateImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.FinishImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.resp.AppendImportChunkResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.BatchImportQuestionResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.CommitImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.CreateImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.FinishImportBatchResponseDTO;
import com.zhoushuo.framework.commono.response.Response;

import java.util.List;
import java.util.Map;


public interface QuestionService {
    /**
     * 批量导入题目
     * @param request
     * @return
     */
    Response<BatchImportQuestionResponseDTO> batchImportQuestions(BatchImportQuestionRequestDTO request);

    Response<CreateImportBatchResponseDTO> createImportBatch(CreateImportBatchRequestDTO request);

    Response<AppendImportChunkResponseDTO> appendImportChunk(AppendImportChunkRequestDTO request);

    Response<FinishImportBatchResponseDTO> finishImportBatch(FinishImportBatchRequestDTO request);

    Response<CommitImportBatchResponseDTO> commitImportBatch(CommitImportBatchRequestDTO request);

    /**
     * 创建题目
     * @param request
     * @return
     */
    Response<QuestionVO> createQuestion(CreateQuestionDTO request);

    /**
     * 查询题目详情
     * @param id 题目ID
     * @return 题目详情
     */
    Response<QuestionVO> getQuestionById(Long id);

    /**
     * 分页查询
     * @param request
     * @return
     */
    Response<?> pageQuestions(QuestionPageQueryDTO request);

    /**
     * 根据id删除题目
     * @param ids
     * @return
     */
    Response<?> deleteQuestions(List<Long> ids);

    // 在接口中添加更新方法
    /**
     * 更新题目
     * @param id 题目ID
     * @param request 更新请求参数
     * @return 更新结果
     */
    Response<QuestionVO> updateQuestion(Long id, UpdateQuestionDTO request);

    Response<?> sendQuestionsToQueue(List<Long> questionIds, String mode);

    /**
     * 审核题目（通过/驳回）
     * @param id 题目ID
     * @param request 审核请求
     * @return 审核结果
     */
    Response<?> reviewQuestion(Long id, ReviewQuestionRequestDTO request);
    
    /**
     * 批量更新成功处理的题目状态
     * @param successResults 成功处理的题目ID和AI结果映射
     * @return 更新成功的数量
     */
    int batchUpdateSuccessQuestions(Map<String, AIProcessResultMessage> successResults);
    
    /**
     * 批量更新失败处理的题目状态
     * @param errorResults 失败处理的题目ID和错误信息映射
     * @return 更新成功的数量
     */
    int batchUpdateFailedQuestions(Map<String, AIProcessResultMessage> errorResults);
}
