package com.zhoushuo.eaqb.excel.parser.biz.rpc;

import com.zhoushuo.eaqb.excel.parser.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.question.bank.api.QuestionFeign;
import com.zhoushuo.eaqb.question.bank.req.BatchImportQuestionRequestDTO;
import com.zhoushuo.eaqb.question.bank.resp.BatchImportQuestionResponseDTO;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class QuestionBankRpcService {
    @Resource
    private QuestionFeign questionfeign;
    public BatchImportQuestionResponseDTO batchImportQuestions(BatchImportQuestionRequestDTO request) {
        Response<BatchImportQuestionResponseDTO> batchImportQuestionResponseDTOResponse = questionfeign.batchImportQuestions(request);
        if (!batchImportQuestionResponseDTOResponse.isSuccess()){
            throw new BizException(ResponseCodeEnum.QUESTION_SERVICE_CALL_FAILED);
        }
        return batchImportQuestionResponseDTOResponse.getData();
    }
}
