package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.model.AIProcessResultMessage;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.CreateQuestionDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.QuestionPageQueryDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.ReviewQuestionRequestDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.UpdateQuestionDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.vo.QuestionVO;
import com.zhoushuo.eaqb.question.bank.biz.service.QuestionService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class QuestionServiceImpl implements QuestionService {

    @Autowired
    private QuestionCrudAppService questionCrudAppService;

    @Autowired
    private QuestionDispatchAppService questionDispatchAppService;

    @Autowired
    private QuestionReviewAppService questionReviewAppService;

    @Autowired
    private QuestionCallbackAppService questionCallbackAppService;
    @Autowired
    private QuestionImportBatchAppService questionImportBatchAppService;

    @Override
    public Response<BatchImportQuestionResponseDTO> batchImportQuestions(BatchImportQuestionRequestDTO request) {
        return questionCrudAppService.batchImportQuestions(request);
    }

    @Override
    public Response<CreateImportBatchResponseDTO> createImportBatch(CreateImportBatchRequestDTO request) {
        return questionImportBatchAppService.createImportBatch(request);
    }

    @Override
    public Response<AppendImportChunkResponseDTO> appendImportChunk(AppendImportChunkRequestDTO request) {
        return questionImportBatchAppService.appendImportChunk(request);
    }

    @Override
    public Response<FinishImportBatchResponseDTO> finishImportBatch(FinishImportBatchRequestDTO request) {
        return questionImportBatchAppService.finishImportBatch(request);
    }

    @Override
    public Response<CommitImportBatchResponseDTO> commitImportBatch(CommitImportBatchRequestDTO request) {
        return questionImportBatchAppService.commitImportBatch(request);
    }

    @Override
    public Response<QuestionVO> createQuestion(CreateQuestionDTO request) {
        return questionCrudAppService.createQuestion(request);
    }

    @Override
    public Response<QuestionVO> getQuestionById(Long id) {
        return questionCrudAppService.getQuestionById(id);
    }

    @Override
    public Response<?> pageQuestions(QuestionPageQueryDTO request) {
        return questionCrudAppService.pageQuestions(request);
    }

    @Override
    public Response<?> deleteQuestions(List<Long> ids) {
        return questionCrudAppService.deleteQuestions(ids);
    }

    @Override
    public Response<QuestionVO> updateQuestion(Long id, UpdateQuestionDTO request) {
        return questionCrudAppService.updateQuestion(id, request);
    }

    @Override
    public Response<?> sendQuestionsToQueue(List<Long> questionIds, String mode) {
        return questionDispatchAppService.sendQuestionsToQueue(questionIds, mode);
    }

    @Override
    public Response<?> reviewQuestion(Long id, ReviewQuestionRequestDTO request) {
        return questionReviewAppService.reviewQuestion(id, request);
    }

    @Override
    public int batchUpdateSuccessQuestions(Map<String, AIProcessResultMessage> successResults) {
        return questionCallbackAppService.batchUpdateSuccessQuestions(successResults);
    }

    @Override
    public int batchUpdateFailedQuestions(Map<String, AIProcessResultMessage> errorResults) {
        return questionCallbackAppService.batchUpdateFailedQuestions(errorResults);
    }
}
