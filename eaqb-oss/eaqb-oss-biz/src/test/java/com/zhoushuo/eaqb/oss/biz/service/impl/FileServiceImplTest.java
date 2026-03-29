package com.zhoushuo.eaqb.oss.biz.service.impl;

import com.zhoushuo.eaqb.oss.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.oss.biz.strategy.FileStrategy;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {

    @Mock
    private FileStrategy fileStrategy;

    @InjectMocks
    private FileServiceImpl fileService;

    @Test
    void uploadExcel_blankObjectName_shouldThrowBizException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "fake".getBytes(StandardCharsets.UTF_8)
        );

        BizException ex = assertThrows(BizException.class, () -> fileService.uploadExcel(file, " "));

        assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void uploadExcel_shouldDelegateToStrategyWithExplicitObjectName() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "fake".getBytes(StandardCharsets.UTF_8)
        );
        when(fileStrategy.uploadExcel(eq(file), eq("eaqb"), eq("9001.xlsx")))
                .thenReturn("excel/1001/9001.xlsx");

        Response<?> response = fileService.uploadExcel(file, "9001.xlsx");

        assertNotNull(response);
        assertEquals("excel/1001/9001.xlsx", response.getData());
        verify(fileStrategy).uploadExcel(file, "eaqb", "9001.xlsx");
    }

    @Test
    void uploadAvatar_shouldDelegateToStrategy() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "fake".getBytes(StandardCharsets.UTF_8)
        );
        when(fileStrategy.uploadAvatar(eq(file), eq("eaqb")))
                .thenReturn("http://oss/eaqb/image/1001/avatar");

        Response<?> response = fileService.uploadAvatar(file);

        assertNotNull(response);
        verify(fileStrategy).uploadAvatar(file, "eaqb");
    }

    @Test
    void uploadBackground_shouldDelegateToStrategy() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "background.png",
                "image/png",
                "fake".getBytes(StandardCharsets.UTF_8)
        );
        when(fileStrategy.uploadBackground(eq(file), eq("eaqb")))
                .thenReturn("http://oss/eaqb/image/1001/background");

        Response<?> response = fileService.uploadBackground(file);

        assertNotNull(response);
        verify(fileStrategy).uploadBackground(file, "eaqb");
    }

    @Test
    void getPresignedDownloadUrl_shouldDelegateByObjectKey() {
        when(fileStrategy.getPresignedDownloadUrl("eaqb", "excel/1001/9001.xlsx"))
                .thenReturn("http://oss/presigned");

        Response<String> response = fileService.getPresignedDownloadUrl("excel/1001/9001.xlsx");

        assertEquals("http://oss/presigned", response.getData());
        verify(fileStrategy).getPresignedDownloadUrl("eaqb", "excel/1001/9001.xlsx");
    }
}
