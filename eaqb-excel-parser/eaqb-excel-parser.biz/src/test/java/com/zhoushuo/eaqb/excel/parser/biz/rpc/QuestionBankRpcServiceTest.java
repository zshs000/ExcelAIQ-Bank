package com.zhoushuo.eaqb.excel.parser.biz.rpc;

import com.zhoushuo.eaqb.question.bank.api.QuestionFeign;
import com.zhoushuo.eaqb.question.bank.req.BatchImportQuestionRequestDTO;
import com.zhoushuo.eaqb.question.bank.resp.BatchImportQuestionResponseDTO;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionBankRpcServiceTest {

    @Mock
    private QuestionFeign questionFeign;

    @InjectMocks
    private QuestionBankRpcService questionBankRpcService;

    @Test
    void batchImportQuestions_downstreamFailResponse_shouldThrowBizException() {
        when(questionFeign.batchImportQuestions(any(BatchImportQuestionRequestDTO.class)))
                .thenReturn(Response.fail("QB-400", "导入请求非法"));

        BizException exception = assertThrows(BizException.class,
                () -> questionBankRpcService.batchImportQuestions(new BatchImportQuestionRequestDTO()));

        assertEquals("QB-400", exception.getErrorCode());
        assertEquals("导入请求非法", exception.getErrorMessage());
    }

    @Test
    void batchImportQuestions_downstreamSuccessWithFailureDto_shouldReturnFailureDto() {
        BatchImportQuestionResponseDTO failureDto = BatchImportQuestionResponseDTO.builder()
                .success(false)
                .totalCount(2)
                .successCount(0)
                .failedCount(2)
                .errorMessage("题库内部导入失败")
                .errorType("QB-500")
                .build();
        when(questionFeign.batchImportQuestions(any(BatchImportQuestionRequestDTO.class)))
                .thenReturn(Response.success(failureDto));

        BatchImportQuestionResponseDTO result =
                questionBankRpcService.batchImportQuestions(new BatchImportQuestionRequestDTO());

        assertFalse(result.isSuccess());
        assertEquals(2, result.getFailedCount());
        assertEquals("题库内部导入失败", result.getErrorMessage());
        assertEquals("QB-500", result.getErrorType());
    }

    @Test
    void batchImportQuestions_downstreamSuccessWithNullData_shouldThrowBizException() {
        when(questionFeign.batchImportQuestions(any(BatchImportQuestionRequestDTO.class)))
                .thenReturn(Response.success(null));

        BizException exception = assertThrows(BizException.class,
                () -> questionBankRpcService.batchImportQuestions(new BatchImportQuestionRequestDTO()));

        assertEquals("EXCEL-20009", exception.getErrorCode());
        assertEquals("题目服务返回空数据", exception.getErrorMessage());
    }
}