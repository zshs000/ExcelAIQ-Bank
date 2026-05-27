package com.zhoushuo.eaqb.excel.parser.biz.service.app;

import com.alibaba.excel.EasyExcel;
import com.sun.net.httpserver.HttpServer;
import com.zhoushuo.eaqb.excel.parser.biz.config.EasyExcelConfig;
import com.zhoushuo.eaqb.excel.parser.biz.domain.dataobject.FileInfoDO;
import com.zhoushuo.eaqb.excel.parser.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.excel.parser.biz.model.vo.ExcelProcessVO;
import com.zhoushuo.eaqb.excel.parser.biz.rpc.OssRpcService;
import com.zhoushuo.eaqb.excel.parser.biz.rpc.QuestionBankRpcService;
import com.zhoushuo.eaqb.excel.parser.biz.service.support.ExcelFileRecordSupport;
import com.zhoushuo.eaqb.question.bank.req.AppendImportChunkRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.CommitImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.CreateImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.FinishImportBatchRequestDTO;
import com.zhoushuo.eaqb.question.bank.resp.AppendImportChunkResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.CommitImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.CreateImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.FindImportBatchByFileResponseDTO;
import com.zhoushuo.eaqb.question.bank.resp.FinishImportBatchResponseDTO;
import com.zhoushuo.eaqb.question.bank.constant.ApiConstants;
import com.zhoushuo.eaqb.question.bank.util.ImportChunkHashUtil;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.common.enums.ProcessStatusEnum;
import com.zhoushuo.framework.common.exception.BizException;
import com.zhoushuo.framework.common.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcelParseAppServiceTest {

    @Mock
    private OssRpcService ossRpcService;
    @Mock
    private QuestionBankRpcService questionBankRpcService;
    @Mock
    private EasyExcelConfig easyExcelConfig;
    @Mock
    private ExcelFileRecordSupport excelFileRecordSupport;

    @InjectMocks
    private ExcelParseAppService excelParseAppService;

    @AfterEach
    void tearDown() {
        LoginUserContextHolder.remove();
    }

    @Test
    void parseExcelFileById_validFile_shouldCreateBatchAppendFinishCommitAndReturnSuccess() throws Exception {
        LoginUserContextHolder.setUserId(123L);
        when(easyExcelConfig.getHeadRowNumber()).thenReturn(1);
        when(easyExcelConfig.getBatchSize()).thenReturn(2);
        FileInfoDO fileInfo = FileInfoDO.builder()
                .id(9001L)
                .userId(123L)
                .objectKey("excel/123/a.xlsx")
                .uploadTime(LocalDateTime.now())
                .status("UPLOADED")
                .build();
        when(excelFileRecordSupport.loadOwnedFile(9001L, 123L)).thenReturn(fileInfo);
        when(excelFileRecordSupport.tryMarkParsing(9001L, 123L)).thenReturn(true);
        when(questionBankRpcService.findImportBatchByFile(any()))
                .thenReturn(FindImportBatchByFileResponseDTO.builder().found(false).build());

        byte[] validBytes = buildExcelBytes(List.of(
                List.of("题目一", "A", "解析一"),
                List.of("题目二", "B", "解析二"),
                List.of("题目三", "C", "解析三")
        ));
        try (LocalHttpFileServer server = new LocalHttpFileServer(validBytes)) {
            when(ossRpcService.getExcelDownloadUrl(any())).thenReturn(server.getUrl());
            when(questionBankRpcService.createImportBatch(any(CreateImportBatchRequestDTO.class)))
                    .thenReturn(CreateImportBatchResponseDTO.builder().batchId(6001L).status("APPENDING").build());
            when(questionBankRpcService.appendImportChunk(any(AppendImportChunkRequestDTO.class)))
                    .thenReturn(AppendImportChunkResponseDTO.builder()
                            .batchId(6001L).chunkNo(1).duplicateChunk(false).receivedChunkCount(1).totalRowCount(2).build())
                    .thenReturn(AppendImportChunkResponseDTO.builder()
                            .batchId(6001L).chunkNo(2).duplicateChunk(false).receivedChunkCount(2).totalRowCount(3).build());
            when(questionBankRpcService.finishImportBatch(any(FinishImportBatchRequestDTO.class)))
                    .thenReturn(FinishImportBatchResponseDTO.builder()
                            .batchId(6001L).status("READY").expectedChunkCount(2).totalRowCount(3).build());
            when(questionBankRpcService.commitImportBatch(any(CommitImportBatchRequestDTO.class)))
                    .thenReturn(CommitImportBatchResponseDTO.builder()
                            .batchId(6001L).status("COMMITTED").importedCount(3).build());

            Response<?> response = excelParseAppService.parseExcelFileById(9001L);

            assertTrue(response.isSuccess());
            ExcelProcessVO vo = (ExcelProcessVO) response.getData();
            assertEquals(ProcessStatusEnum.SUCCESS.getValue(), vo.getProcessStatus());
            assertEquals(3, vo.getTotalCount());
            assertEquals(3, vo.getSuccessCount());
            assertEquals(0, vo.getFailCount());
            verify(excelFileRecordSupport).markFileStatus(9001L, ExcelFileRecordSupport.FILE_STATUS_PARSED);

            ArgumentCaptor<CreateImportBatchRequestDTO> createCaptor = ArgumentCaptor.forClass(CreateImportBatchRequestDTO.class);
            verify(questionBankRpcService).createImportBatch(createCaptor.capture());
            assertEquals(9001L, createCaptor.getValue().getFileId());
            assertEquals(2, createCaptor.getValue().getChunkSize());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<AppendImportChunkRequestDTO> appendCaptor = ArgumentCaptor.forClass(AppendImportChunkRequestDTO.class);
            verify(questionBankRpcService, org.mockito.Mockito.times(2)).appendImportChunk(appendCaptor.capture());
            List<AppendImportChunkRequestDTO> chunkRequests = appendCaptor.getAllValues();
            assertEquals(2, chunkRequests.get(0).getRowCount());
            assertEquals(1, chunkRequests.get(1).getRowCount());
            assertEquals(ImportChunkHashUtil.HASH_VERSION_V2, chunkRequests.get(0).getHashVersion());
            assertEquals(ImportChunkHashUtil.HASH_VERSION_V2, chunkRequests.get(1).getHashVersion());
            assertEquals("题目一", chunkRequests.get(0).getRows().get(0).getContent());
            assertEquals("题目三", chunkRequests.get(1).getRows().get(0).getContent());

            ArgumentCaptor<FinishImportBatchRequestDTO> finishCaptor = ArgumentCaptor.forClass(FinishImportBatchRequestDTO.class);
            verify(questionBankRpcService).finishImportBatch(finishCaptor.capture());
            assertEquals(2, finishCaptor.getValue().getExpectedChunkCount());
            assertEquals(3, finishCaptor.getValue().getExpectedRowCount());
            verify(questionBankRpcService).commitImportBatch(any(CommitImportBatchRequestDTO.class));
        }
    }

    @Test
    void parseExcelFileById_committedBatchExists_shouldRecoverSuccessWithoutDownloadingOrCreatingBatch() {
        LoginUserContextHolder.setUserId(123L);
        FileInfoDO fileInfo = FileInfoDO.builder()
                .id(9011L)
                .userId(123L)
                .objectKey("excel/123/committed.xlsx")
                .status("FAILED")
                .build();
        when(excelFileRecordSupport.loadOwnedFile(9011L, 123L)).thenReturn(fileInfo);
        when(excelFileRecordSupport.tryMarkParsing(9011L, 123L)).thenReturn(true);
        when(questionBankRpcService.findImportBatchByFile(any()))
                .thenReturn(FindImportBatchByFileResponseDTO.builder()
                        .found(true)
                        .batchId(6101L)
                        .status("COMMITTED")
                        .totalRowCount(4)
                        .importedCount(4)
                        .build());

        Response<?> response = excelParseAppService.parseExcelFileById(9011L);

        assertTrue(response.isSuccess());
        ExcelProcessVO vo = (ExcelProcessVO) response.getData();
        assertEquals(ProcessStatusEnum.SUCCESS.getValue(), vo.getProcessStatus());
        assertEquals(4, vo.getTotalCount());
        assertEquals(4, vo.getSuccessCount());
        verify(excelFileRecordSupport).markFileStatus(9011L, ExcelFileRecordSupport.FILE_STATUS_PARSED);
        verifyNoMoreInteractions(ossRpcService);
        verify(questionBankRpcService, never()).createImportBatch(any(CreateImportBatchRequestDTO.class));
        verify(questionBankRpcService, never()).appendImportChunk(any(AppendImportChunkRequestDTO.class));
        verify(questionBankRpcService, never()).finishImportBatch(any(FinishImportBatchRequestDTO.class));
        verify(questionBankRpcService, never()).commitImportBatch(any(CommitImportBatchRequestDTO.class));
    }

    @Test
    void parseExcelFileById_readyBatchExists_shouldCommitExistingBatchWithoutDownloadingOrAppending() {
        LoginUserContextHolder.setUserId(123L);
        FileInfoDO fileInfo = FileInfoDO.builder()
                .id(9012L)
                .userId(123L)
                .objectKey("excel/123/ready.xlsx")
                .status("FAILED")
                .build();
        when(excelFileRecordSupport.loadOwnedFile(9012L, 123L)).thenReturn(fileInfo);
        when(excelFileRecordSupport.tryMarkParsing(9012L, 123L)).thenReturn(true);
        when(questionBankRpcService.findImportBatchByFile(any()))
                .thenReturn(FindImportBatchByFileResponseDTO.builder()
                        .found(true)
                        .batchId(6102L)
                        .status("READY")
                        .totalRowCount(5)
                        .build());
        when(questionBankRpcService.commitImportBatch(any(CommitImportBatchRequestDTO.class)))
                .thenReturn(CommitImportBatchResponseDTO.builder()
                        .batchId(6102L)
                        .status("COMMITTED")
                        .importedCount(5)
                        .build());

        Response<?> response = excelParseAppService.parseExcelFileById(9012L);

        assertTrue(response.isSuccess());
        ExcelProcessVO vo = (ExcelProcessVO) response.getData();
        assertEquals(5, vo.getTotalCount());
        assertEquals(5, vo.getSuccessCount());
        verify(excelFileRecordSupport).markFileStatus(9012L, ExcelFileRecordSupport.FILE_STATUS_PARSED);
        verifyNoMoreInteractions(ossRpcService);
        verify(questionBankRpcService, never()).createImportBatch(any(CreateImportBatchRequestDTO.class));
        verify(questionBankRpcService, never()).appendImportChunk(any(AppendImportChunkRequestDTO.class));
        verify(questionBankRpcService, never()).finishImportBatch(any(FinishImportBatchRequestDTO.class));
        ArgumentCaptor<CommitImportBatchRequestDTO> commitCaptor = ArgumentCaptor.forClass(CommitImportBatchRequestDTO.class);
        verify(questionBankRpcService).commitImportBatch(commitCaptor.capture());
        assertEquals(6102L, commitCaptor.getValue().getBatchId());
    }

    @Test
    void parseExcelFileById_bindingIdsBatchExists_shouldFinishThenCommitWithoutDownloadingOrAppending() {
        LoginUserContextHolder.setUserId(123L);
        FileInfoDO fileInfo = FileInfoDO.builder()
                .id(9016L)
                .userId(123L)
                .objectKey("excel/123/binding.xlsx")
                .status("FAILED")
                .build();
        when(excelFileRecordSupport.loadOwnedFile(9016L, 123L)).thenReturn(fileInfo);
        when(excelFileRecordSupport.tryMarkParsing(9016L, 123L)).thenReturn(true);
        when(questionBankRpcService.findImportBatchByFile(any()))
                .thenReturn(FindImportBatchByFileResponseDTO.builder()
                        .found(true)
                        .batchId(6106L)
                        .status("BINDING_IDS")
                        .expectedChunkCount(2)
                        .receivedChunkCount(2)
                        .totalRowCount(5)
                        .build());
        when(questionBankRpcService.finishImportBatch(any(FinishImportBatchRequestDTO.class)))
                .thenReturn(FinishImportBatchResponseDTO.builder()
                        .batchId(6106L)
                        .status("READY")
                        .expectedChunkCount(2)
                        .totalRowCount(5)
                        .build());
        when(questionBankRpcService.commitImportBatch(any(CommitImportBatchRequestDTO.class)))
                .thenReturn(CommitImportBatchResponseDTO.builder()
                        .batchId(6106L)
                        .status("COMMITTED")
                        .importedCount(5)
                        .build());

        Response<?> response = excelParseAppService.parseExcelFileById(9016L);

        assertTrue(response.isSuccess());
        ExcelProcessVO vo = (ExcelProcessVO) response.getData();
        assertEquals(5, vo.getTotalCount());
        assertEquals(5, vo.getSuccessCount());
        verify(excelFileRecordSupport).markFileStatus(9016L, ExcelFileRecordSupport.FILE_STATUS_PARSED);
        verifyNoMoreInteractions(ossRpcService);
        verify(questionBankRpcService, never()).createImportBatch(any(CreateImportBatchRequestDTO.class));
        verify(questionBankRpcService, never()).appendImportChunk(any(AppendImportChunkRequestDTO.class));

        ArgumentCaptor<FinishImportBatchRequestDTO> finishCaptor = ArgumentCaptor.forClass(FinishImportBatchRequestDTO.class);
        verify(questionBankRpcService).finishImportBatch(finishCaptor.capture());
        assertEquals(6106L, finishCaptor.getValue().getBatchId());
        assertEquals(2, finishCaptor.getValue().getExpectedChunkCount());
        assertEquals(5, finishCaptor.getValue().getExpectedRowCount());

        ArgumentCaptor<CommitImportBatchRequestDTO> commitCaptor = ArgumentCaptor.forClass(CommitImportBatchRequestDTO.class);
        verify(questionBankRpcService).commitImportBatch(commitCaptor.capture());
        assertEquals(6106L, commitCaptor.getValue().getBatchId());
    }

    @Test
    void parseExcelFileById_appendingBatchExists_shouldAbortOldBatchAndImportFromScratch() throws Exception {
        LoginUserContextHolder.setUserId(123L);
        when(easyExcelConfig.getHeadRowNumber()).thenReturn(1);
        when(easyExcelConfig.getBatchSize()).thenReturn(2);
        FileInfoDO fileInfo = FileInfoDO.builder()
                .id(9013L)
                .userId(123L)
                .objectKey("excel/123/appending.xlsx")
                .status("FAILED")
                .build();
        when(excelFileRecordSupport.loadOwnedFile(9013L, 123L)).thenReturn(fileInfo);
        when(excelFileRecordSupport.tryMarkParsing(9013L, 123L)).thenReturn(true);
        when(questionBankRpcService.findImportBatchByFile(any()))
                .thenReturn(FindImportBatchByFileResponseDTO.builder()
                        .found(true)
                        .batchId(6103L)
                        .status("APPENDING")
                        .build());

        byte[] validBytes = buildExcelBytes(List.of(
                List.of("题目一", "A", "解析一"),
                List.of("题目二", "B", "解析二")
        ));
        try (LocalHttpFileServer server = new LocalHttpFileServer(validBytes)) {
            when(ossRpcService.getExcelDownloadUrl(any())).thenReturn(server.getUrl());
            when(questionBankRpcService.createImportBatch(any(CreateImportBatchRequestDTO.class)))
                    .thenReturn(CreateImportBatchResponseDTO.builder().batchId(6203L).status("APPENDING").build());
            when(questionBankRpcService.appendImportChunk(any(AppendImportChunkRequestDTO.class)))
                    .thenReturn(AppendImportChunkResponseDTO.builder()
                            .batchId(6203L).chunkNo(1).duplicateChunk(false).receivedChunkCount(1).totalRowCount(2).build());
            when(questionBankRpcService.finishImportBatch(any(FinishImportBatchRequestDTO.class)))
                    .thenReturn(FinishImportBatchResponseDTO.builder()
                            .batchId(6203L).status("READY").expectedChunkCount(1).totalRowCount(2).build());
            when(questionBankRpcService.commitImportBatch(any(CommitImportBatchRequestDTO.class)))
                    .thenReturn(CommitImportBatchResponseDTO.builder()
                            .batchId(6203L).status("COMMITTED").importedCount(2).build());

            Response<?> response = excelParseAppService.parseExcelFileById(9013L);

            assertTrue(response.isSuccess());
            verify(questionBankRpcService).abortAppendingImportBatch(6103L, "restart file import");
            verify(questionBankRpcService).createImportBatch(any(CreateImportBatchRequestDTO.class));
            verify(questionBankRpcService).appendImportChunk(any(AppendImportChunkRequestDTO.class));
            verify(questionBankRpcService).finishImportBatch(any(FinishImportBatchRequestDTO.class));
        }
    }

    @Test
    void parseExcelFileById_appendingAbortLostRace_shouldReloadBatchAndCommitReadyBatch() {
        LoginUserContextHolder.setUserId(123L);
        FileInfoDO fileInfo = FileInfoDO.builder()
                .id(9014L)
                .userId(123L)
                .objectKey("excel/123/race.xlsx")
                .status("FAILED")
                .build();
        when(excelFileRecordSupport.loadOwnedFile(9014L, 123L)).thenReturn(fileInfo);
        when(excelFileRecordSupport.tryMarkParsing(9014L, 123L)).thenReturn(true);
        when(questionBankRpcService.findImportBatchByFile(any()))
                .thenReturn(FindImportBatchByFileResponseDTO.builder()
                        .found(true)
                        .batchId(6104L)
                        .status("APPENDING")
                        .build())
                .thenReturn(FindImportBatchByFileResponseDTO.builder()
                        .found(true)
                        .batchId(6104L)
                        .status("READY")
                        .totalRowCount(6)
                        .build());
        org.mockito.Mockito.doThrow(new BizException(ApiConstants.QUESTION_IMPORT_BATCH_STATUS_ILLEGAL, "导入批次状态非法"))
                .when(questionBankRpcService).abortAppendingImportBatch(6104L, "restart file import");
        when(questionBankRpcService.commitImportBatch(any(CommitImportBatchRequestDTO.class)))
                .thenReturn(CommitImportBatchResponseDTO.builder()
                        .batchId(6104L)
                        .status("COMMITTED")
                        .importedCount(6)
                        .build());

        Response<?> response = excelParseAppService.parseExcelFileById(9014L);

        assertTrue(response.isSuccess());
        ExcelProcessVO vo = (ExcelProcessVO) response.getData();
        assertEquals(6, vo.getTotalCount());
        assertEquals(6, vo.getSuccessCount());
        verifyNoMoreInteractions(ossRpcService);
        verify(questionBankRpcService, never()).createImportBatch(any(CreateImportBatchRequestDTO.class));
        verify(questionBankRpcService).commitImportBatch(any(CommitImportBatchRequestDTO.class));
    }

    @Test
    void parseExcelFileById_appendingAbortKeepsSeeingStaleStatus_shouldStopAfterLimitedReloads() {
        LoginUserContextHolder.setUserId(123L);
        FileInfoDO fileInfo = FileInfoDO.builder()
                .id(9015L)
                .userId(123L)
                .objectKey("excel/123/stale-appending.xlsx")
                .status("FAILED")
                .build();
        when(excelFileRecordSupport.loadOwnedFile(9015L, 123L)).thenReturn(fileInfo);
        when(excelFileRecordSupport.tryMarkParsing(9015L, 123L)).thenReturn(true);
        when(questionBankRpcService.findImportBatchByFile(any()))
                .thenReturn(FindImportBatchByFileResponseDTO.builder()
                        .found(true)
                        .batchId(6105L)
                        .status("APPENDING")
                        .build());
        org.mockito.Mockito.doThrow(new BizException(ApiConstants.QUESTION_IMPORT_BATCH_STATUS_ILLEGAL, "stale APPENDING"))
                .when(questionBankRpcService).abortAppendingImportBatch(6105L, "restart file import");

        BizException ex = assertThrows(BizException.class, () -> excelParseAppService.parseExcelFileById(9015L));

        assertEquals(ResponseCodeEnum.QUESTION_SERVICE_CALL_FAILED.getErrorCode(), ex.getErrorCode());
        verify(questionBankRpcService, org.mockito.Mockito.times(3)).findImportBatchByFile(any());
        verify(questionBankRpcService, org.mockito.Mockito.times(3)).abortAppendingImportBatch(6105L, "restart file import");
        verifyNoMoreInteractions(ossRpcService);
        verify(questionBankRpcService, never()).createImportBatch(any(CreateImportBatchRequestDTO.class));
    }

    @Test
    void parseExcelFileById_appendChunkFailed_shouldMarkFailedAndAbortFinishAndCommit() throws Exception {
        LoginUserContextHolder.setUserId(123L);
        when(easyExcelConfig.getHeadRowNumber()).thenReturn(1);
        when(easyExcelConfig.getBatchSize()).thenReturn(2);
        FileInfoDO fileInfo = FileInfoDO.builder()
                .id(9007L)
                .userId(123L)
                .objectKey("excel/123/g.xlsx")
                .status("UPLOADED")
                .build();
        when(excelFileRecordSupport.loadOwnedFile(9007L, 123L)).thenReturn(fileInfo);
        when(excelFileRecordSupport.tryMarkParsing(9007L, 123L)).thenReturn(true);
        when(questionBankRpcService.findImportBatchByFile(any()))
                .thenReturn(FindImportBatchByFileResponseDTO.builder().found(false).build());

        byte[] validBytes = buildExcelBytes(List.of(
                List.of("题目一", "A", "解析一"),
                List.of("题目二", "B", "解析二"),
                List.of("题目三", "C", "解析三")
        ));
        try (LocalHttpFileServer server = new LocalHttpFileServer(validBytes)) {
            when(ossRpcService.getExcelDownloadUrl(any())).thenReturn(server.getUrl());
            when(questionBankRpcService.createImportBatch(any(CreateImportBatchRequestDTO.class)))
                    .thenReturn(CreateImportBatchResponseDTO.builder().batchId(6001L).status("APPENDING").build());
            when(questionBankRpcService.appendImportChunk(any(AppendImportChunkRequestDTO.class)))
                    .thenThrow(new BizException("QB-500", "追加分块失败"));

            BizException ex = assertThrows(BizException.class, () -> excelParseAppService.parseExcelFileById(9007L));

            assertEquals("QB-500", ex.getErrorCode());
            assertEquals("追加分块失败", ex.getErrorMessage());
            verify(excelFileRecordSupport).markFileStatusQuietly(9007L, ExcelFileRecordSupport.FILE_STATUS_FAILED);
            verify(questionBankRpcService, never()).finishImportBatch(any(FinishImportBatchRequestDTO.class));
            verify(questionBankRpcService, never()).commitImportBatch(any(CommitImportBatchRequestDTO.class));
        }
    }

    @Test
    void parseExcelFileById_whenAlreadyClaimed_shouldReturnFailWithoutRemoteCalls() {
        LoginUserContextHolder.setUserId(123L);
        FileInfoDO fileInfo = FileInfoDO.builder()
                .id(9004L)
                .userId(123L)
                .objectKey("excel/123/d.xlsx")
                .status("UPLOADED")
                .build();
        when(excelFileRecordSupport.loadOwnedFile(9004L, 123L)).thenReturn(fileInfo);
        when(excelFileRecordSupport.tryMarkParsing(9004L, 123L)).thenReturn(false);

        Response<?> response = excelParseAppService.parseExcelFileById(9004L);

        assertFalse(response.isSuccess());
        assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), response.getErrorCode());
        verifyNoMoreInteractions(ossRpcService, questionBankRpcService);
    }

    @Test
    void parseExcelFileById_presignedDownloadUrlBlank_shouldReturnFriendlyRetryMessageAndMarkFailed() {
        LoginUserContextHolder.setUserId(123L);
        FileInfoDO fileInfo = FileInfoDO.builder()
                .id(9005L)
                .userId(123L)
                .objectKey("excel/123/e.xlsx")
                .status("UPLOADED")
                .build();
        when(excelFileRecordSupport.loadOwnedFile(9005L, 123L)).thenReturn(fileInfo);
        when(excelFileRecordSupport.tryMarkParsing(9005L, 123L)).thenReturn(true);
        when(questionBankRpcService.findImportBatchByFile(any()))
                .thenReturn(FindImportBatchByFileResponseDTO.builder().found(false).build());
        when(ossRpcService.getExcelDownloadUrl(any())).thenReturn("  ");

        BizException ex = assertThrows(BizException.class, () -> excelParseAppService.parseExcelFileById(9005L));

        assertEquals(ResponseCodeEnum.FILE_READ_ERROR.getErrorCode(), ex.getErrorCode());
        assertEquals("文件服务暂时不可用，请稍后重试", ex.getErrorMessage());
        verify(excelFileRecordSupport).markFileStatusQuietly(9005L, ExcelFileRecordSupport.FILE_STATUS_FAILED);
    }

    private static byte[] buildExcelBytes(List<List<String>> rows) throws IOException {
        List<List<String>> head = Arrays.asList(
                List.of("题目"),
                List.of("答案"),
                List.of("解析")
        );
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out).head(head).sheet("Sheet1").doWrite(rows);
            return out.toByteArray();
        }
    }

    private static class LocalHttpFileServer implements AutoCloseable {
        private final HttpServer server;
        private final String url;

        LocalHttpFileServer(byte[] fileBytes) throws IOException {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/file.xlsx", exchange -> {
                exchange.getResponseHeaders().set("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                exchange.sendResponseHeaders(200, fileBytes.length);
                exchange.getResponseBody().write(fileBytes);
                exchange.close();
            });
            server.createContext("/404", exchange -> {
                byte[] body = "not found".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            this.url = "http://127.0.0.1:" + server.getAddress().getPort() + "/file.xlsx";
        }

        String getUrl() {
            return url;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
