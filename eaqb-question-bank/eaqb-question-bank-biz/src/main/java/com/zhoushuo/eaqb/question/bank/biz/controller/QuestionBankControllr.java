package com.zhoushuo.eaqb.question.bank.biz.controller;

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

    @PostMapping("/import-batch/create")
    @ApiOperationLog(description = "创建题目导入批次")
    public Response<CreateImportBatchResponseDTO> createImportBatch(@Valid @RequestBody CreateImportBatchRequestDTO request) {
        return questionService.createImportBatch(request);
    }

    @PostMapping("/import-batch/append-chunk")
    @ApiOperationLog(description = "追加题目导入分块")
    public Response<AppendImportChunkResponseDTO> appendImportChunk(@Valid @RequestBody AppendImportChunkRequestDTO request) {
        return questionService.appendImportChunk(request);
    }

    @PostMapping("/import-batch/finish")
    @ApiOperationLog(description = "结束题目导入分块接收")
    public Response<FinishImportBatchResponseDTO> finishImportBatch(@Valid @RequestBody FinishImportBatchRequestDTO request) {
        return questionService.finishImportBatch(request);
    }

    @PostMapping("/import-batch/commit")
    @ApiOperationLog(description = "提交题目导入批次")
    public Response<CommitImportBatchResponseDTO> commitImportBatch(@Valid @RequestBody CommitImportBatchRequestDTO request) {
        return questionService.commitImportBatch(request);
    }
}
