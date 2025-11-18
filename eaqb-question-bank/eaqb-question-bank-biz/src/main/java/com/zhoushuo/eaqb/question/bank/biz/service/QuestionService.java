package com.zhoushuo.eaqb.question.bank.biz.service;

import com.zhoushuo.eaqb.question.bank.biz.model.dto.CreateQuestionDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.QuestionPageQueryDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.UpdateQuestionDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.vo.QuestionVO;
import com.zhoushuo.eaqb.question.bank.req.BatchImportQuestionRequestDTO;
import com.zhoushuo.eaqb.question.bank.resp.BatchImportQuestionResponseDTO;
import com.zhoushuo.framework.commono.response.Response;
import org.springframework.stereotype.Service;

import java.util.List;


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

    Response<?> sendQuestionsToQueue(List<Long> questionIds);
    /**
     * 更新题目状态为待审查
     * @param questionId 题目ID
     * @param answer AI生成的答案
     */
    void updateQuestionStatusToReview(String questionId, String answer);
}
