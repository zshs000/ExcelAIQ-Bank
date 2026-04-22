package com.zhoushuo.eaqb.question.bank.biz.controller;

import com.zhoushuo.eaqb.question.bank.biz.model.dto.CreateQuestionDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.QuestionPageQueryDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.ReviewQuestionRequestDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.dto.SendToQueueRequestDTO;
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
     * @param request 发送请求（题目ID列表 + 处理模式）
     * @return 发送结果
     */
    @PostMapping("/send-to-queue")
    public Response<?> sendQuestionsToQueue(@Valid @RequestBody SendToQueueRequestDTO request) {
        return questionService.sendQuestionsToQueue(request.getQuestionIds(), request.getMode());
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
     * 查询题目详情
     * @param id 题目ID
     * @return 题目详情
     */
    @GetMapping("/{id}")
    public Response<QuestionVO> getQuestionById(@PathVariable("id") Long id) {
        return questionService.getQuestionById(id);
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
    public Response<QuestionVO> updateQuestion(@PathVariable("id") Long id, @Valid @RequestBody UpdateQuestionDTO request) {
        return questionService.updateQuestion(id, request);
    }

    /**
     * 审核题目（通过/驳回）
     * @param id 题目ID
     * @param request 审核动作
     * @return 审核结果
     */
    @PostMapping("/{id}/review")
    public Response<?> reviewQuestion(@PathVariable("id") Long id, @Valid @RequestBody ReviewQuestionRequestDTO request) {
        return questionService.reviewQuestion(id, request);
    }


}
