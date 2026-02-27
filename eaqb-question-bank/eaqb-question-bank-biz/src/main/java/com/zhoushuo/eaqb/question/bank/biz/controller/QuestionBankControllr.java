package com.zhoushuo.eaqb.question.bank.biz.controller;

import com.zhoushuo.eaqb.question.bank.biz.service.QuestionService;
import com.zhoushuo.eaqb.question.bank.req.BatchImportQuestionRequestDTO;
import com.zhoushuo.eaqb.question.bank.resp.BatchImportQuestionResponseDTO;
import com.zhoushuo.framework.biz.operationlog.aspect.ApiOperationLog;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/question")
@Validated
public class QuestionBankControllr {
    @Autowired
    private QuestionService questionService;

    @PostMapping("/batch-import")
    @ApiOperationLog(description = "批量导入题目")
    public Response<BatchImportQuestionResponseDTO> batchImportQuestions(@Valid @RequestBody BatchImportQuestionRequestDTO request) {
        return questionService.batchImportQuestions(request);

    }
}
