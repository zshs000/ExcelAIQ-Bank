package com.zhoushuo.eaqb.excel.parser.biz.service.impl;

import com.alibaba.excel.EasyExcel;
import com.sun.net.httpserver.HttpServer;
import com.zhoushuo.eaqb.excel.parser.biz.domain.dataobject.ExcelPreUploadRecordDO;
import com.zhoushuo.eaqb.excel.parser.biz.domain.dataobject.FileInfoDO;
import com.zhoushuo.eaqb.excel.parser.biz.domain.mapper.ExcelPreUploadRecordDOMapper;
import com.zhoushuo.eaqb.excel.parser.biz.domain.mapper.FileInfoDOMapper;
import com.zhoushuo.eaqb.excel.parser.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.excel.parser.biz.model.dto.ExcelFileUploadDTO;
import com.zhoushuo.eaqb.excel.parser.biz.model.vo.ExcelFileUploadVO;
import com.zhoushuo.eaqb.excel.parser.biz.model.vo.ExcelProcessVO;
import com.zhoushuo.eaqb.excel.parser.biz.rpc.DistributedIdGeneratorRpcService;
import com.zhoushuo.eaqb.excel.parser.biz.rpc.OssRpcService;
import com.zhoushuo.eaqb.excel.parser.biz.rpc.QuestionBankRpcService;
import com.zhoushuo.eaqb.question.bank.req.BatchImportQuestionRequestDTO;
import com.zhoushuo.eaqb.question.bank.resp.BatchImportQuestionResponseDTO;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.eumns.ProcessStatusEnum;
import com.zhoushuo.framework.commono.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * ExcelFileServiceImpl 单服务测试。
 * 覆盖上传校验、错误查询、解析导入和权限控制的关键路径。
 *
 * 设计约束：
 * 1. 仅测试 excel-parser 服务内部行为，不依赖真实 DB/OSS/question-bank。
 * 2. DAO 与 RPC 均用 Mockito mock，确保测试稳定且执行快速。
 * 3. 解析阶段使用本地临时 HTTP Server 模拟 OSS 文件下载，避免外网依赖。
 */
@ExtendWith(MockitoExtension.class)
class ExcelFileServiceImplTest {

    @Mock
    private OssRpcService ossRpcService;
    @Mock
    private FileInfoDOMapper fileInfoDOMapper;
    @Mock
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Mock
    private ExcelPreUploadRecordDOMapper excelPreUploadRecordDOMapper;
    @Mock
    private QuestionBankRpcService questionBankRpcService;

    @InjectMocks
    private ExcelFileServiceImpl excelFileService;

    @AfterEach
    void tearDown() {
        // 每个用例结束后清理 ThreadLocal 登录态，防止跨用例污染。
        LoginUserContextHolder.remove();
    }

    @Test
    void uploadAExcel_validExcel_shouldReturnFileIdAndUploadedStatus() throws Exception {
        // Given: 当前用户已登录，且 OSS 上传、分布式 ID 生成都返回正常结果。
        LoginUserContextHolder.setUserId(123L);
        when(ossRpcService.uploadFile(any())).thenReturn("http://oss/eaqb/excel/123/a.xlsx");
        when(distributedIdGeneratorRpcService.getFileId()).thenReturn("9001");

        // 构造一个模板正确的 Excel（表头：题目/答案/解析，数据行合法）。
        ExcelFileUploadDTO dto = new ExcelFileUploadDTO();
        dto.setFile(new MockMultipartFile(
                "file",
                "valid.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                buildExcelBytes(List.of(List.of("题目一", "A", "解析一")))
        ));

        // When: 执行上传校验流程。
        Response<?> response = excelFileService.uploadAExcel(dto);

        // Then: 上传应成功，返回 fileId 和 UPLOADED 状态，不应产生 preUploadId。
        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        ExcelFileUploadVO vo = (ExcelFileUploadVO) response.getData();
        assertEquals(9001L, vo.getFileId());
        assertEquals("UPLOADED", vo.getStatus());
        assertNull(vo.getPreUploadId());
        // 成功场景应写入正式文件记录表。
        verify(fileInfoDOMapper, times(1)).insert(any(FileInfoDO.class));
    }

    @Test
    void uploadAExcel_invalidExcel_shouldReturnPreUploadIdAndFailStatus() throws Exception {
        // Given: 当前用户已登录，预上传 ID 生成可用。
        LoginUserContextHolder.setUserId(123L);
        when(distributedIdGeneratorRpcService.getPreFileId()).thenReturn("7001");

        // 捕获插入的预上传记录，用于断言失败信息是否被持久化。
        AtomicReference<ExcelPreUploadRecordDO> inserted = new AtomicReference<>();
        when(excelPreUploadRecordDOMapper.insert(any())).thenAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            return 1;
        });

        // 构造一个“表头不匹配”的 Excel（第一列故意写成“错 题”）。
        ExcelFileUploadDTO dto = new ExcelFileUploadDTO();
        dto.setFile(new MockMultipartFile(
                "file",
                "invalid.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                buildExcelBytes(List.of(List.of("错 题", "A", "解析一")))
        ));

        // When: 执行上传校验流程。
        Response<?> response = excelFileService.uploadAExcel(dto);

        // Then: 外层调用仍成功返回（业务失败可追溯），并返回 preUploadId + FAIL。
        assertTrue(response.isSuccess());
        ExcelFileUploadVO vo = (ExcelFileUploadVO) response.getData();
        assertEquals(7001L, vo.getPreUploadId());
        assertEquals("FAIL", vo.getVerifyStatus());
        // 失败细节应落库到预上传记录表，供后续按 preUploadId 查询。
        assertNotNull(inserted.get());
        assertEquals("FAIL", inserted.get().getVerifyStatus());
    }

    @Test
    void getValidationErrors_shouldReturnErrorListForOwner() {
        // Given: 预上传失败记录属于当前用户，并包含多行错误信息。
        LoginUserContextHolder.setUserId(123L);
        ExcelPreUploadRecordDO record = ExcelPreUploadRecordDO.builder()
                .id(7001L)
                .userId(123L)
                .errorMessages("第2行错误\n第3行错误")
                .build();
        when(excelPreUploadRecordDOMapper.selectById(7001L)).thenReturn(record);

        // When: 按 preUploadId 查询校验错误明细。
        Response<?> response = excelFileService.getValidationErrors(7001L);

        // Then: 返回拆分后的错误列表，且顺序与原始记录一致。
        assertTrue(response.isSuccess());
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) response.getData();
        assertEquals(2, errors.size());
        assertEquals("第2行错误", errors.get(0));
    }

    @Test
    void parseExcelFileById_validFile_shouldParseAndImportSuccess() throws Exception {
        // Given: fileId 存在且归属当前用户，状态为可解析。
        LoginUserContextHolder.setUserId(123L);
        FileInfoDO fileInfo = FileInfoDO.builder()
                .id(9001L)
                .userId(123L)
                .ossUrl("http://oss/eaqb/excel/123/a.xlsx")
                .uploadTime(LocalDateTime.now())
                .status("UPLOADED")
                .build();
        when(fileInfoDOMapper.selectByPrimaryKey(9001L)).thenReturn(fileInfo);
        when(fileInfoDOMapper.tryMarkParsing(9001L, 123L)).thenReturn(1);

        // 构造合法 Excel；通过本地 HTTP 服务模拟 OSS 短链下载。
        byte[] validBytes = buildExcelBytes(List.of(List.of("题目一", "A", "解析一")));
        try (LocalHttpFileServer server = new LocalHttpFileServer(validBytes)) {
            when(ossRpcService.getShortUrl(any())).thenReturn(server.getUrl());
            // mock 下游题库批量导入成功响应。
            when(questionBankRpcService.batchImportQuestions(any())).thenReturn(
                    BatchImportQuestionResponseDTO.builder()
                            .success(true)
                            .totalCount(1)
                            .successCount(1)
                            .failedCount(0)
                            .build()
            );

            // When: 执行 parse-by-id（下载 -> 解析 -> 调用题库导入）。
            Response<?> response = excelFileService.parseExcelFileById(9001L);

            // Then: 处理状态应成功，计数与下游导入结果一致。
            assertTrue(response.isSuccess());
            ExcelProcessVO vo = (ExcelProcessVO) response.getData();
            assertEquals(ProcessStatusEnum.SUCCESS.getValue(), vo.getProcessStatus());
            assertEquals(1, vo.getTotalCount());
            assertEquals(1, vo.getSuccessCount());
            assertEquals(0, vo.getFailCount());

            verify(fileInfoDOMapper).tryMarkParsing(9001L, 123L);
            ArgumentCaptor<FileInfoDO> statusCaptor = ArgumentCaptor.forClass(FileInfoDO.class);
            verify(fileInfoDOMapper).updateByPrimaryKeySelective(statusCaptor.capture());
            assertEquals("PARSED", statusCaptor.getValue().getStatus());

            // 同时断言传给 question-bank 的请求中确实有 1 条题目数据。
            ArgumentCaptor<BatchImportQuestionRequestDTO> captor = ArgumentCaptor.forClass(BatchImportQuestionRequestDTO.class);
            verify(questionBankRpcService).batchImportQuestions(captor.capture());
            assertEquals(1, captor.getValue().getQuestions().size());
        }
    }

    @Test
    void parseExcelFileById_nonOwner_shouldReturnNoPermission() {
        // Given: fileId 存在，但归属不是当前用户（越权场景）。
        LoginUserContextHolder.setUserId(123L);
        FileInfoDO fileInfo = FileInfoDO.builder()
                .id(9002L)
                .userId(999L)
                .ossUrl("http://oss/eaqb/excel/999/b.xlsx")
                .build();
        when(fileInfoDOMapper.selectByPrimaryKey(9002L)).thenReturn(fileInfo);

        // When: 当前用户尝试解析他人文件。
        Response<?> response = excelFileService.parseExcelFileById(9002L);

        // Then: 应直接返回 NO_PERMISSION，且不调用下游题库服务。
        assertFalse(response.isSuccess());
        assertEquals(ResponseCodeEnum.NO_PERMISSION.getErrorCode(), response.getErrorCode());
        verifyNoInteractions(questionBankRpcService);
    }

    @Test
    void parseExcelFileById_fileNotFound_shouldReturnRecordNotFound() {
        // Given: fileId 不存在。
        LoginUserContextHolder.setUserId(123L);
        when(fileInfoDOMapper.selectByPrimaryKey(9999L)).thenReturn(null);

        // When
        Response<?> response = excelFileService.parseExcelFileById(9999L);

        // Then: 直接返回记录不存在，不继续访问 OSS 或题库服务。
        assertFalse(response.isSuccess());
        assertEquals(ResponseCodeEnum.RECORD_NOT_FOUND.getErrorCode(), response.getErrorCode());
        verifyNoInteractions(ossRpcService, questionBankRpcService);
    }

    @Test
    void parseExcelFileById_importFailed_shouldReturnFailedProcessVO() throws Exception {
        // Given: 文件归属正确，但下游题库导入返回业务失败。
        LoginUserContextHolder.setUserId(123L);
        FileInfoDO fileInfo = FileInfoDO.builder()
                .id(9003L)
                .userId(123L)
                .ossUrl("http://oss/eaqb/excel/123/c.xlsx")
                .status("UPLOADED")
                .build();
        when(fileInfoDOMapper.selectByPrimaryKey(9003L)).thenReturn(fileInfo);
        when(fileInfoDOMapper.tryMarkParsing(9003L, 123L)).thenReturn(1);

        byte[] validBytes = buildExcelBytes(List.of(List.of("题目一", "A", "解析一")));
        try (LocalHttpFileServer server = new LocalHttpFileServer(validBytes)) {
            when(ossRpcService.getShortUrl(any())).thenReturn(server.getUrl());
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

            // When
            Response<?> response = excelFileService.parseExcelFileById(9003L);

            // Then: 外层调用成功返回，但处理结果为 FAILED，且补齐 fileId / 耗时等字段。
            assertTrue(response.isSuccess());
            ExcelProcessVO vo = (ExcelProcessVO) response.getData();
            assertEquals(ProcessStatusEnum.FAILED.getValue(), vo.getProcessStatus());
            assertEquals(1, vo.getTotalCount());
            assertEquals(0, vo.getSuccessCount());
            assertEquals(1, vo.getFailCount());
            assertEquals("题库导入失败", vo.getErrorMessage());
            assertEquals("9003", vo.getFileId());
            assertTrue(vo.getProcessTimeMs() >= 0);
            assertTrue(vo.getFinishTime() > 0);

            verify(fileInfoDOMapper).tryMarkParsing(9003L, 123L);
            ArgumentCaptor<FileInfoDO> statusCaptor = ArgumentCaptor.forClass(FileInfoDO.class);
            verify(fileInfoDOMapper).updateByPrimaryKeySelective(statusCaptor.capture());
            assertEquals("FAILED", statusCaptor.getValue().getStatus());
        }
    }

    @Test
    void parseExcelFileById_whenAlreadyClaimed_shouldReturnFailWithoutImport() {
        LoginUserContextHolder.setUserId(123L);
        FileInfoDO fileInfo = FileInfoDO.builder()
                .id(9004L)
                .userId(123L)
                .ossUrl("http://oss/eaqb/excel/123/d.xlsx")
                .status("UPLOADED")
                .build();
        when(fileInfoDOMapper.selectByPrimaryKey(9004L)).thenReturn(fileInfo);
        when(fileInfoDOMapper.tryMarkParsing(9004L, 123L)).thenReturn(0);

        Response<?> response = excelFileService.parseExcelFileById(9004L);

        assertFalse(response.isSuccess());
        assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), response.getErrorCode());
        assertEquals("文件状态已变化，无法重复解析", response.getMessage());
        verify(fileInfoDOMapper).tryMarkParsing(9004L, 123L);
        verifyNoInteractions(ossRpcService, questionBankRpcService);
        verify(fileInfoDOMapper, never()).updateByPrimaryKeySelective(any());
    }

    /**
     * 构造一个最小可用的 xlsx 二进制内容：
     * - 固定表头使用服务当前约定的模板列名（题目/答案/解析）；
     * - rows 参数只表示数据行，便于各测试按场景快速组装输入。
     */
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

    /**
     * 轻量本地 HTTP 文件服务，用来替代真实 OSS 下载链路。
     * 目的：在不依赖外网和对象存储的情况下，覆盖 parse-by-id 中的 URL 下载流程。
     */
    private static class LocalHttpFileServer implements AutoCloseable {
        private final HttpServer server;
        private final String url;

        LocalHttpFileServer(byte[] fileBytes) throws IOException {
            // 端口 0 表示由系统自动分配空闲端口，避免测试端口冲突。
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

        // 返回可直接访问的本地文件 URL，供 ossRpcService.getShortUrl mock 使用。
        String getUrl() {
            return url;
        }

        @Override
        public void close() {
            // 关闭嵌入式 HTTP 服务，释放端口和线程资源。
            server.stop(0);
        }
    }
}
