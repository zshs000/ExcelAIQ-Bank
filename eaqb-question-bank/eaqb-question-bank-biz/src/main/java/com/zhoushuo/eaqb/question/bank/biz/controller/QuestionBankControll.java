package com.zhoushuo.eaqb.question.bank.biz.controller;

import com.zhoushuo.eaqb.question.bank.biz.service.QuestionService;
import com.zhoushuo.eaqb.question.bank.req.BatchImportQuestionRequestDTO;
import com.zhoushuo.framework.biz.operationlog.aspect.ApiOperationLog;
import com.zhoushuo.framework.commono.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/question")
public class QuestionBankControll {
    @Autowired
    private QuestionService questionService;
    @RequestMapping("/batch-import")
    @ApiOperationLog(description = "批量导入题目")
    public Response<?> batchImportQuestions(@RequestBody BatchImportQuestionRequestDTO request) {
        return questionService.batchImportQuestions(request);

    }
}
