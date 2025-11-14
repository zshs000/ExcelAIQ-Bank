package com.zhoushuo.eaqb.question.bank.biz.controller;

import com.zhoushuo.eaqb.question.bank.biz.model.dto.CreateQuestionDTO;
import com.zhoushuo.eaqb.question.bank.biz.model.vo.QuestionVO;
import com.zhoushuo.eaqb.question.bank.biz.service.QuestionService;
import com.zhoushuo.framework.commono.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/question")
public class QuestionExternalController {
    @Autowired
    private QuestionService questionService;


    /**
     * 创建题目
     * @param request 创建题目请求参数
     * @return 创建结果
     */
    @PostMapping
    public Response<QuestionVO> createQuestion(@RequestBody CreateQuestionDTO request) {
        return questionService.createQuestion(request);
    }

}
