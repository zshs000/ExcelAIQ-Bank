package com.zhoushuo.eaqb.excel.parser.biz.service.app;

import com.alibaba.excel.EasyExcel;
import com.zhoushuo.eaqb.excel.parser.biz.domain.dataobject.ExcelPreUploadRecordDO;
import com.zhoushuo.eaqb.excel.parser.biz.domain.dataobject.FileInfoDO;
import com.zhoushuo.eaqb.excel.parser.biz.domain.mapper.ExcelPreUploadRecordDOMapper;
import com.zhoushuo.eaqb.excel.parser.biz.domain.mapper.FileInfoDOMapper;
import com.zhoushuo.eaqb.excel.parser.biz.model.dto.ExcelFileUploadDTO;
import com.zhoushuo.eaqb.excel.parser.biz.model.vo.ExcelFileUploadVO;
import com.zhoushuo.eaqb.excel.parser.biz.rpc.DistributedIdGeneratorRpcService;
import com.zhoushuo.eaqb.excel.parser.biz.rpc.OssRpcService;
import com.zhoushuo.eaqb.excel.parser.biz.service.support.ExcelFileRecordSupport;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.common.exception.BizException;
import com.zhoushuo.framework.common.response.Response;
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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcelUploadAppServiceTest {

    @Mock
    private OssRpcService ossRpcService;
    @Mock
    private FileInfoDOMapper fileInfoDOMapper;
    @Mock
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Mock
    private ExcelPreUploadRecordDOMapper excelPreUploadRecordDOMapper;
    @Mock
    private ExcelFileRecordSupport excelFileRecordSupport;

    @InjectMocks
    private ExcelUploadAppService excelUploadAppService;

    @AfterEach
    void tearDown() {
        LoginUserContextHolder.remove();
    }

    @Test
    void uploadAExcel_validExcel_shouldReturnFileIdAndUploadedStatus() throws Exception {
        LoginUserContextHolder.setUserId(123L);
        when(distributedIdGeneratorRpcService.getFileId()).thenReturn("9001");
        when(ossRpcService.uploadExcel(any(), eq("9001.xlsx"))).thenReturn("excel/123/9001.xlsx");
        doAnswer(invocation -> {
            FileInfoDO fileInfoDO = invocation.getArgument(0);
            fileInfoDO.setObjectKey(invocation.getArgument(1));
            fileInfoDO.setStatus(ExcelFileRecordSupport.FILE_STATUS_UPLOADED);
            return null;
        }).when(excelFileRecordSupport).markUploadSuccess(any(FileInfoDO.class), eq("excel/123/9001.xlsx"));

        ExcelFileUploadDTO dto = new ExcelFileUploadDTO();
        dto.setFile(new MockMultipartFile(
                "file",
                "valid.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                buildExcelBytes(List.of(List.of("题目一", "A", "解析一")))
        ));

        Response<?> response = excelUploadAppService.uploadAExcel(dto);

        assertTrue(response.isSuccess());
        ExcelFileUploadVO vo = (ExcelFileUploadVO) response.getData();
        assertEquals(9001L, vo.getFileId());
        assertEquals("UPLOADED", vo.getStatus());
        assertNull(vo.getPreUploadId());
        verify(fileInfoDOMapper, times(1)).insert(any(FileInfoDO.class));
        verify(excelFileRecordSupport).markUploadSuccess(any(FileInfoDO.class), eq("excel/123/9001.xlsx"));
    }

    @Test
    void uploadAExcel_invalidExcel_shouldReturnPreUploadIdAndFailStatus() throws Exception {
        LoginUserContextHolder.setUserId(123L);
        when(distributedIdGeneratorRpcService.getPreFileId()).thenReturn("7001");

        AtomicReference<ExcelPreUploadRecordDO> inserted = new AtomicReference<>();
        when(excelPreUploadRecordDOMapper.insert(any())).thenAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            return 1;
        });

        ExcelFileUploadDTO dto = new ExcelFileUploadDTO();
        dto.setFile(new MockMultipartFile(
                "file",
                "invalid.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                buildExcelBytes(List.of(List.of("错 题", "A", "解析一")))
        ));

        Response<?> response = excelUploadAppService.uploadAExcel(dto);

        assertTrue(response.isSuccess());
        ExcelFileUploadVO vo = (ExcelFileUploadVO) response.getData();
        assertEquals(7001L, vo.getPreUploadId());
        assertEquals("FAIL", vo.getVerifyStatus());
        assertNotNull(inserted.get());
        assertEquals("FAIL", inserted.get().getVerifyStatus());
    }

    @Test
    void uploadAExcel_whenHeaderIsNotOnFirstRow_shouldReturnValidationFailure() throws Exception {
        LoginUserContextHolder.setUserId(123L);
        when(distributedIdGeneratorRpcService.getPreFileId()).thenReturn("7002");

        AtomicReference<ExcelPreUploadRecordDO> inserted = new AtomicReference<>();
        when(excelPreUploadRecordDOMapper.insert(any())).thenAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            return 1;
        });

        ExcelFileUploadDTO dto = new ExcelFileUploadDTO();
        dto.setFile(new MockMultipartFile(
                "file",
                "header-not-first.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                buildRawExcelBytes(List.of(
                        List.of("", "", ""),
                        List.of("题目", "答案", "解析"),
                        List.of("题目一", "A", "解析一")
                ))
        ));

        Response<?> response = excelUploadAppService.uploadAExcel(dto);

        assertTrue(response.isSuccess());
        ExcelFileUploadVO vo = (ExcelFileUploadVO) response.getData();
        assertEquals(7002L, vo.getPreUploadId());
        assertEquals("FAIL", vo.getVerifyStatus());
        assertNotNull(inserted.get());
        assertTrue(inserted.get().getErrorMessages().contains("第1行必须是表头"));
    }

    @Test
    void uploadAExcel_whenOssUploadFails_shouldMarkUploadFailedAndPropagateOriginalBizException() throws Exception {
        LoginUserContextHolder.setUserId(123L);
        when(distributedIdGeneratorRpcService.getFileId()).thenReturn("9002");
        when(ossRpcService.uploadExcel(any(), eq("9002.xlsx")))
                .thenThrow(new BizException("OSS-2", "upload failed"));

        ExcelFileUploadDTO dto = new ExcelFileUploadDTO();
        dto.setFile(new MockMultipartFile(
                "file",
                "valid.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                buildExcelBytes(List.of(List.of("题目一", "A", "解析一")))
        ));

        BizException ex = assertThrows(BizException.class, () -> excelUploadAppService.uploadAExcel(dto));

        assertEquals("OSS-2", ex.getErrorCode());
        assertEquals("upload failed", ex.getErrorMessage());
        verify(fileInfoDOMapper).insert(any(FileInfoDO.class));
        verify(excelFileRecordSupport).markUploadStatus(9002L, ExcelFileRecordSupport.FILE_STATUS_UPLOAD_FAILED, null);
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

    private static byte[] buildRawExcelBytes(List<List<String>> rows) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out).sheet("Sheet1").doWrite(rows);
            return out.toByteArray();
        }
    }
}
