package com.zhoushuo.eaqb.question.bank.api;

import com.zhoushuo.eaqb.question.bank.constant.ApiConstants;
import com.zhoushuo.eaqb.question.bank.req.AppendImportChunkRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.BatchImportQuestionRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.CommitImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.CreateImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.FinishImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.resp.BatchImportQuestionResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.AppendImportChunkResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.CommitImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.CreateImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.FinishImportBatchResponseDTO;
import com.zhoushuo.framework.common.response.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface QuestionFeign {

    public static final String PREFIX = "/question";

    /**
     * 批量导入题目接口
     * @param request 批量导入请求
     * @return 导入结果
     */
    @PostMapping(value = PREFIX+"/batch-import")
    Response<BatchImportQuestionResponseDTO> batchImportQuestions(@RequestBody BatchImportQuestionRequestDTO request);

    @PostMapping(value = PREFIX + "/import-batch/create")
    Response<CreateImportBatchResponseDTO> createImportBatch(@RequestBody CreateImportBatchRequestDTO request);

    @PostMapping(value = PREFIX + "/import-batch/append-chunk")
    Response<AppendImportChunkResponseDTO> appendImportChunk(@RequestBody AppendImportChunkRequestDTO request);

    @PostMapping(value = PREFIX + "/import-batch/finish")
    Response<FinishImportBatchResponseDTO> finishImportBatch(@RequestBody FinishImportBatchRequestDTO request);

    @PostMapping(value = PREFIX + "/import-batch/commit")
    Response<CommitImportBatchResponseDTO> commitImportBatch(@RequestBody CommitImportBatchRequestDTO request);


}
