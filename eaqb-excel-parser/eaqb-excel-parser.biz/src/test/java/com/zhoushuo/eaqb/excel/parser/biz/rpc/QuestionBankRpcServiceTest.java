package com.zhoushuo.eaqb.excel.parser.biz.rpc;

import com.zhoushuo.eaqb.question.bank.api.QuestionFeign;
import com.zhoushuo.eaqb.question.bank.req.AppendImportChunkRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.CommitImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.CreateImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.FinishImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.resp.AppendImportChunkResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.CommitImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.CreateImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.FinishImportBatchResponseDTO;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionBankRpcServiceTest {

    @Mock
    private QuestionFeign questionFeign;

    @InjectMocks
    private QuestionBankRpcService questionBankRpcService;

    @Test
    void createImportBatch_downstreamFailResponse_shouldThrowBizException() {
        when(questionFeign.createImportBatch(any(CreateImportBatchRequestDTO.class)))
                .thenReturn(Response.fail("QB-400", "创建批次失败"));

        BizException exception = assertThrows(BizException.class,
                () -> questionBankRpcService.createImportBatch(new CreateImportBatchRequestDTO()));

        assertEquals("QB-400", exception.getErrorCode());
        assertEquals("创建批次失败", exception.getErrorMessage());
    }

    @Test
    void appendImportChunk_downstreamSuccessWithNullData_shouldThrowBizException() {
        when(questionFeign.appendImportChunk(any(AppendImportChunkRequestDTO.class)))
                .thenReturn(Response.success(null));

        BizException exception = assertThrows(BizException.class,
                () -> questionBankRpcService.appendImportChunk(new AppendImportChunkRequestDTO()));

        assertEquals("EXCEL-20009", exception.getErrorCode());
        assertEquals("题库服务返回空数据", exception.getErrorMessage());
    }

    @Test
    void finishAndCommitImportBatch_success_shouldReturnTypedDto() {
        when(questionFeign.finishImportBatch(any(FinishImportBatchRequestDTO.class)))
                .thenReturn(Response.success(FinishImportBatchResponseDTO.builder()
                        .batchId(6001L)
                        .status("READY")
                        .expectedChunkCount(2)
                        .totalRowCount(3)
                        .build()));
        when(questionFeign.commitImportBatch(any(CommitImportBatchRequestDTO.class)))
                .thenReturn(Response.success(CommitImportBatchResponseDTO.builder()
                        .batchId(6001L)
                        .status("COMMITTED")
                        .importedCount(3)
                        .build()));

        FinishImportBatchResponseDTO finishResult =
                questionBankRpcService.finishImportBatch(new FinishImportBatchRequestDTO());
        CommitImportBatchResponseDTO commitResult =
                questionBankRpcService.commitImportBatch(new CommitImportBatchRequestDTO());

        assertEquals("READY", finishResult.getStatus());
        assertEquals(2, finishResult.getExpectedChunkCount());
        assertEquals("COMMITTED", commitResult.getStatus());
        assertEquals(3, commitResult.getImportedCount());
    }

    @Test
    void createAndAppendImportBatch_success_shouldReturnTypedDto() {
        when(questionFeign.createImportBatch(any(CreateImportBatchRequestDTO.class)))
                .thenReturn(Response.success(CreateImportBatchResponseDTO.builder()
                        .batchId(7001L)
                        .status("APPENDING")
                        .build()));
        when(questionFeign.appendImportChunk(any(AppendImportChunkRequestDTO.class)))
                .thenReturn(Response.success(AppendImportChunkResponseDTO.builder()
                        .batchId(7001L)
                        .chunkNo(1)
                        .duplicateChunk(false)
                        .receivedChunkCount(1)
                        .totalRowCount(2)
                        .build()));

        CreateImportBatchResponseDTO createResult =
                questionBankRpcService.createImportBatch(new CreateImportBatchRequestDTO());
        AppendImportChunkResponseDTO appendResult =
                questionBankRpcService.appendImportChunk(new AppendImportChunkRequestDTO());

        assertEquals(7001L, createResult.getBatchId());
        assertEquals("APPENDING", createResult.getStatus());
        assertTrue(!appendResult.isDuplicateChunk());
        assertEquals(1, appendResult.getReceivedChunkCount());
        assertEquals(2, appendResult.getTotalRowCount());
    }
}
