package com.zhoushuo.eaqb.question.bank.biz.controller;

import com.zhoushuo.eaqb.question.bank.biz.model.dto.CreateQuestionDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.QuestionPageQueryDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.UpdateQuestionDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.vo.QuestionVO;
import com.zhoushuo.eaqb.question.bank.biz.service.QuestionService;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/question")
@Validated
public class QuestionExternalController {
    @Autowired
    private QuestionService questionService;

    /**
     * 批量发送题目到消息队列
     * @param questionIds 题目ID列表
     * @return 发送结果
     */
    @PostMapping("/send-to-queue")
    public Response<?> sendQuestionsToQueue(@RequestBody @NotEmpty(message = "题目ID列表不能为空") List<Long> questionIds) {
        return questionService.sendQuestionsToQueue(questionIds);
    }


    /**
     * 创建题目
     * @param request 创建题目请求参数
     * @return 创建结果
     */
    @PostMapping
    public Response<QuestionVO> createQuestion(@Valid @RequestBody CreateQuestionDTO request) {
        return questionService.createQuestion(request);
    }

    /**
     * 分页条件查询
     * @param request
     * @return
     */
    @PostMapping("/page")
    public Response<?> pageQuestions(@Valid @RequestBody QuestionPageQueryDTO request) {
        return questionService.pageQuestions(request);
    }

    /**
     * 删除
     * @param ids
     * @return
     */

    @DeleteMapping("/questions")
    public Response<?> batchDelete(@RequestParam @NotEmpty(message = "删除ID列表不能为空") List<Long> ids) {
        return questionService.deleteQuestions(ids);
    }

    /**
     * 更新题目
     * @param id 题目ID
     * @param request 更新请求参数
     * @return 更新结果
     */
    @PatchMapping("/{id}")
    public Response<QuestionVO> updateQuestion(@PathVariable Long id, @Valid @RequestBody UpdateQuestionDTO request) {
        return questionService.updateQuestion(id, request);
    }



}
