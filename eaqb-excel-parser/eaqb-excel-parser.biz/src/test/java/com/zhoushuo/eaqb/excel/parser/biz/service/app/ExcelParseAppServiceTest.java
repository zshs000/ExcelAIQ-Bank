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
import com.zhoushuo.eaqb.question.bank.resp.BatchImportQuestionResponseDTO;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.eumns.ProcessStatusEnum;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    void parseExcelFileById_validFile_shouldParseAndImportSuccess() throws Exception {
        LoginUserContextHolder.setUserId(123L);
        when(easyExcelConfig.getHeadRowNumber()).thenReturn(1);
        FileInfoDO fileInfo = FileInfoDO.builder()
                .id(9001L)
                .userId(123L)
                .objectKey("excel/123/a.xlsx")
                .uploadTime(LocalDateTime.now())
                .status("UPLOADED")
                .build();
        when(excelFileRecordSupport.loadOwnedFile(9001L, 123L)).thenReturn(fileInfo);
        when(excelFileRecordSupport.tryMarkParsing(9001L, 123L)).thenReturn(true);

        byte[] validBytes = buildExcelBytes(List.of(List.of("题目一", "A", "解析一")));
        try (LocalHttpFileServer server = new LocalHttpFileServer(validBytes)) {
            when(ossRpcService.getExcelDownloadUrl(any())).thenReturn(server.getUrl());
            when(questionBankRpcService.batchImportQuestions(any())).thenReturn(
                    BatchImportQuestionResponseDTO.builder()
                            .success(true)
                            .totalCount(1)
                            .successCount(1)
                            .failedCount(0)
                            .build()
            );

            Response<?> response = excelParseAppService.parseExcelFileById(9001L);

            assertTrue(response.isSuccess());
            ExcelProcessVO vo = (ExcelProcessVO) response.getData();
            assertEquals(ProcessStatusEnum.SUCCESS.getValue(), vo.getProcessStatus());
            assertEquals(1, vo.getTotalCount());
            assertEquals(1, vo.getSuccessCount());
            assertEquals(0, vo.getFailCount());
            verify(excelFileRecordSupport).markFileStatus(9001L, ExcelFileRecordSupport.FILE_STATUS_PARSED);
        }
    }

    @Test
    void parseExcelFileById_importFailed_shouldReturnFailedProcessVO() throws Exception {
        LoginUserContextHolder.setUserId(123L);
        when(easyExcelConfig.getHeadRowNumber()).thenReturn(1);
        FileInfoDO fileInfo = FileInfoDO.builder()
                .id(9003L)
                .userId(123L)
                .objectKey("excel/123/c.xlsx")
                .status("UPLOADED")
                .build();
        when(excelFileRecordSupport.loadOwnedFile(9003L, 123L)).thenReturn(fileInfo);
        when(excelFileRecordSupport.tryMarkParsing(9003L, 123L)).thenReturn(true);

        byte[] validBytes = buildExcelBytes(List.of(List.of("题目一", "A", "解析一")));
        try (LocalHttpFileServer server = new LocalHttpFileServer(validBytes)) {
            when(ossRpcService.getExcelDownloadUrl(any())).thenReturn(server.getUrl());
            when(questionBankRpcService.batchImportQuestions(any())).thenReturn(
                    BatchImportQuestionResponseDTO.builder()
                            .success(false)
                            .totalCount(1)
                            .successCount(0)
                            .failedCount(1)
                            .errorMessage("题库导入失败")
                            .errorType("QB-FAIL")
                            .build()
            );

            Response<?> response = excelParseAppService.parseExcelFileById(9003L);

            assertTrue(response.isSuccess());
            ExcelProcessVO vo = (ExcelProcessVO) response.getData();
            assertEquals(ProcessStatusEnum.FAILED.getValue(), vo.getProcessStatus());
            assertEquals(1, vo.getTotalCount());
            assertEquals(0, vo.getSuccessCount());
            assertEquals(1, vo.getFailCount());
            assertEquals("题库导入失败", vo.getErrorMessage());
            verify(excelFileRecordSupport).markFileStatus(9003L, ExcelFileRecordSupport.FILE_STATUS_FAILED);
        }
    }

    @Test
    void parseExcelFileById_importRpcFailed_shouldMarkFailedAndRethrowBizException() throws Exception {
        LoginUserContextHolder.setUserId(123L);
        when(easyExcelConfig.getHeadRowNumber()).thenReturn(1);
        FileInfoDO fileInfo = FileInfoDO.builder()
                .id(9007L)
                .userId(123L)
                .objectKey("excel/123/g.xlsx")
                .status("UPLOADED")
                .build();
        when(excelFileRecordSupport.loadOwnedFile(9007L, 123L)).thenReturn(fileInfo);
        when(excelFileRecordSupport.tryMarkParsing(9007L, 123L)).thenReturn(true);

        byte[] validBytes = buildExcelBytes(List.of(List.of("题目一", "A", "解析一")));
        try (LocalHttpFileServer server = new LocalHttpFileServer(validBytes)) {
            when(ossRpcService.getExcelDownloadUrl(any())).thenReturn(server.getUrl());
            when(questionBankRpcService.batchImportQuestions(any()))
                    .thenThrow(new BizException("QB-503", "题库服务暂时不可用"));

            BizException ex = assertThrows(BizException.class, () -> excelParseAppService.parseExcelFileById(9007L));

            assertEquals("QB-503", ex.getErrorCode());
            assertEquals("题库服务暂时不可用", ex.getErrorMessage());
            verify(excelFileRecordSupport).markFileStatusQuietly(9007L, ExcelFileRecordSupport.FILE_STATUS_FAILED);
        }
    }

    @Test
    void parseExcelFileById_whenAlreadyClaimed_shouldReturnFailWithoutImport() {
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
        assertEquals("文件状态已变化，无法重复解析", response.getMessage());
        verifyNoInteractions(ossRpcService, questionBankRpcService);
        verify(excelFileRecordSupport, never()).markFileStatusQuietly(any(), any());
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
        when(ossRpcService.getExcelDownloadUrl(any())).thenReturn("  ");

        BizException ex = assertThrows(BizException.class, () -> excelParseAppService.parseExcelFileById(9005L));

        assertEquals(ResponseCodeEnum.FILE_READ_ERROR.getErrorCode(), ex.getErrorCode());
        assertEquals("文件服务暂时不可用，请稍后重试", ex.getErrorMessage());
        verify(excelFileRecordSupport).markFileStatusQuietly(9005L, ExcelFileRecordSupport.FILE_STATUS_FAILED);
        verifyNoInteractions(questionBankRpcService);
    }

    @Test
    void parseExcelFileById_whenMarkFileStatusFails_shouldStillReturnSuccessResult() throws Exception {
        LoginUserContextHolder.setUserId(123L);
        when(easyExcelConfig.getHeadRowNumber()).thenReturn(1);
        FileInfoDO fileInfo = FileInfoDO.builder()
                .id(9010L)
                .userId(123L)
                .objectKey("excel/123/j.xlsx")
                .status("UPLOADED")
                .build();
        when(excelFileRecordSupport.loadOwnedFile(9010L, 123L)).thenReturn(fileInfo);
        when(excelFileRecordSupport.tryMarkParsing(9010L, 123L)).thenReturn(true);
        doThrow(new RuntimeException("db down")).when(excelFileRecordSupport)
                .markFileStatus(9010L, ExcelFileRecordSupport.FILE_STATUS_PARSED);

        byte[] validBytes = buildExcelBytes(List.of(List.of("题目一", "A", "解析一")));
        try (LocalHttpFileServer server = new LocalHttpFileServer(validBytes)) {
            when(ossRpcService.getExcelDownloadUrl(any())).thenReturn(server.getUrl());
            when(questionBankRpcService.batchImportQuestions(any())).thenReturn(
                    BatchImportQuestionResponseDTO.builder()
                            .success(true)
                            .totalCount(1)
                            .successCount(1)
                            .failedCount(0)
                            .build()
            );

            BizException ex = assertThrows(BizException.class, () -> excelParseAppService.parseExcelFileById(9010L));

            assertEquals(ResponseCodeEnum.SYSTEM_ERROR.getErrorCode(), ex.getErrorCode());
            verify(excelFileRecordSupport).markFileStatusQuietly(9010L, ExcelFileRecordSupport.FILE_STATUS_FAILED);
        }
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
