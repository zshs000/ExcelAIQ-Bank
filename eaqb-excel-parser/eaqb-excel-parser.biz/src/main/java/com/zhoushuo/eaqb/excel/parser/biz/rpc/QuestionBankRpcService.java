package com.zhoushuo.eaqb.excel.parser.biz.rpc;

import com.zhoushuo.eaqb.excel.parser.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.question.bank.api.QuestionFeign;
import com.zhoushuo.eaqb.question.bank.req.BatchImportQuestionRequestDTO;
import com.zhoushuo.eaqb.question.bank.resp.BatchImportQuestionResponseDTO;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class QuestionBankRpcService {
    @Resource
    private QuestionFeign questionfeign;

    public BatchImportQuestionResponseDTO batchImportQuestions(BatchImportQuestionRequestDTO request) {
        try {
            Response<BatchImportQuestionResponseDTO> response = questionfeign.batchImportQuestions(request);
            if (response == null) {
                throw new BizException(ResponseCodeEnum.QUESTION_SERVICE_CALL_FAILED);
            }
            if (!response.isSuccess()) {
                throw new BizException(response.getErrorCode(), response.getMessage());
            }

            BatchImportQuestionResponseDTO data = response.getData();
            if (data == null) {
                throw new BizException(ResponseCodeEnum.QUESTION_SERVICE_CALL_FAILED.getErrorCode(), "题目服务返回空数据");
            }
            return data;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用题目服务批量导入失败", e);
            throw new BizException(ResponseCodeEnum.QUESTION_SERVICE_CALL_FAILED);
        }
    }
}