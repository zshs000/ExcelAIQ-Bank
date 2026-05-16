package com.zhoushuo.eaqb.excel.parser.biz.rpc;

import com.zhoushuo.eaqb.excel.parser.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.question.bank.api.QuestionFeign;
import com.zhoushuo.eaqb.question.bank.req.AppendImportChunkRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.CommitImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.CreateImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.FinishImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.resp.AppendImportChunkResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.CommitImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.CreateImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.FinishImportBatchResponseDTO;
import com.zhoushuo.framework.common.exception.BizException;
import com.zhoushuo.framework.common.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class QuestionBankRpcService {

    private static final String EMPTY_DATA_MESSAGE = "题库服务返回空数据";

    @Resource
    private QuestionFeign questionFeign;

    public CreateImportBatchResponseDTO createImportBatch(CreateImportBatchRequestDTO request) {
        return invokeForData(() -> questionFeign.createImportBatch(request), "调用题库服务创建导入批次失败");
    }

    public AppendImportChunkResponseDTO appendImportChunk(AppendImportChunkRequestDTO request) {
        return invokeForData(() -> questionFeign.appendImportChunk(request), "调用题库服务追加导入分块失败");
    }

    public FinishImportBatchResponseDTO finishImportBatch(FinishImportBatchRequestDTO request) {
        return invokeForData(() -> questionFeign.finishImportBatch(request), "调用题库服务结束导入批次失败");
    }

    public CommitImportBatchResponseDTO commitImportBatch(CommitImportBatchRequestDTO request) {
        return invokeForData(() -> questionFeign.commitImportBatch(request), "调用题库服务提交导入批次失败");
    }

    private <T> T invokeForData(FeignCall<T> call, String logMessage) {
        try {
            Response<T> response = call.execute();
            if (response == null) {
                throw new BizException(ResponseCodeEnum.QUESTION_SERVICE_CALL_FAILED);
            }
            if (!response.isSuccess()) {
                throw new BizException(response.getErrorCode(), response.getMessage());
            }
            T data = response.getData();
            if (data == null) {
                throw new BizException(ResponseCodeEnum.QUESTION_SERVICE_CALL_FAILED.getErrorCode(), EMPTY_DATA_MESSAGE);
            }
            return data;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error(logMessage, e);
            throw new BizException(ResponseCodeEnum.QUESTION_SERVICE_CALL_FAILED);
        }
    }

    @FunctionalInterface
    private interface FeignCall<T> {
        Response<T> execute();
    }
}
