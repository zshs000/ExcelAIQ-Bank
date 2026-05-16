package com.zhoushuo.eaqb.excel.parser.biz.rpc;

import com.zhoushuo.eaqb.oss.api.FileFeignApi;
import com.zhoushuo.framework.common.exception.BizException;
import com.zhoushuo.framework.common.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OssRpcServiceTest {

    @Mock
    private FileFeignApi fileFeignApi;

    @InjectMocks
    private OssRpcService ossRpcService;

    @Test
    void getExcelDownloadUrl_shouldRetryUntilSuccess() {
        when(fileFeignApi.getExcelDownloadUrl(anyString()))
                .thenReturn(Response.fail("OSS-1", "temporary down"))
                .thenReturn(Response.fail("OSS-1", "temporary down"))
                .thenReturn(Response.success("http://oss/presigned-url"));

        String result = ossRpcService.getExcelDownloadUrl("excel/123/9001.xlsx");

        assertEquals("http://oss/presigned-url", result);
        verify(fileFeignApi, times(3)).getExcelDownloadUrl("excel/123/9001.xlsx");
    }

    @Test
    void getExcelDownloadUrl_shouldRetryThreeTimesAndThrowBizExceptionWhenResponsesKeepFailing() {
        when(fileFeignApi.getExcelDownloadUrl(anyString()))
                .thenReturn(Response.fail("OSS-1", "temporary down"));

        BizException exception = assertThrows(BizException.class,
                () -> ossRpcService.getExcelDownloadUrl("excel/123/9001.xlsx"));

        assertEquals("OSS-1", exception.getErrorCode());
        assertEquals("temporary down", exception.getErrorMessage());
        verify(fileFeignApi, times(3)).getExcelDownloadUrl("excel/123/9001.xlsx");
    }

    @Test
    void getExcelDownloadUrl_shouldRetryThreeTimesAndRethrowWhenExceptionsKeepHappening() {
        when(fileFeignApi.getExcelDownloadUrl(anyString()))
                .thenThrow(new RuntimeException("network down"));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> ossRpcService.getExcelDownloadUrl("excel/123/9001.xlsx"));

        assertEquals("network down", exception.getMessage());
        verify(fileFeignApi, times(3)).getExcelDownloadUrl("excel/123/9001.xlsx");
    }

    @Test
    void uploadExcel_shouldThrowBizExceptionWhenResponseIsFail() {
        when(fileFeignApi.uploadExcel(any(), anyString()))
                .thenReturn(Response.fail("OSS-2", "upload failed"));

        BizException exception = assertThrows(BizException.class,
                () -> ossRpcService.uploadExcel(
                        new MockMultipartFile("file", "a.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                new byte[]{1}),
                        "9001.xlsx"));

        assertEquals("OSS-2", exception.getErrorCode());
        assertEquals("upload failed", exception.getErrorMessage());
    }
}
