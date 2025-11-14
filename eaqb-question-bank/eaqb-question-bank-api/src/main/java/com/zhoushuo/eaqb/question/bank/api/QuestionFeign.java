package com.zhoushuo.eaqb.question.bank.api;

import com.zhoushuo.eaqb.question.bank.constant.ApiConstants;
import com.zhoushuo.eaqb.question.bank.req.BatchImportQuestionRequestDTO;
import com.zhoushuo.eaqb.question.bank.resp.BatchImportQuestionResponseDTO;

import com.zhoushuo.framework.commono.response.Response;
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


}
