package com.zhoushuo.eaqb.question.bank.biz.service;

import com.zhoushuo.eaqb.question.bank.biz.model.dto.CreateQuestionDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.vo.QuestionVO;
import com.zhoushuo.eaqb.question.bank.req.BatchImportQuestionRequestDTO;
import com.zhoushuo.eaqb.question.bank.resp.BatchImportQuestionResponseDTO;
import com.zhoushuo.framework.commono.response.Response;
import org.springframework.stereotype.Service;


public interface QuestionService {
    /**
     * 批量导入题目
     * @param request
     * @return
     */
    Response<?> batchImportQuestions(BatchImportQuestionRequestDTO request);

    /**
     * 创建题目
     * @param request
     * @return
     */
    Response<QuestionVO> createQuestion(CreateQuestionDTO request);
}
