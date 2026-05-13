package com.zhoushuo.eaqb.oss.biz.service.impl;

import com.zhoushuo.eaqb.oss.biz.config.PresignProperties;
import com.zhoushuo.eaqb.oss.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.oss.biz.strategy.FileStrategy;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.common.exception.BizException;
import com.zhoushuo.framework.common.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

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

    @BeforeEach
    void setUp() {
        PresignProperties presignProperties = new PresignProperties();
        presignProperties.setExcelDownloadExpireSeconds(600);
        presignProperties.setImageViewExpireSeconds(86400);
        ReflectionTestUtils.setField(fileService, "presignProperties", presignProperties);
    }

    @AfterEach
    void tearDown() {
        LoginUserContextHolder.remove();
    }

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
                .thenReturn("image/1001/avatar");

        Response<?> response = fileService.uploadAvatar(file);

        assertNotNull(response);
        assertEquals("image/1001/avatar", response.getData());
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
                .thenReturn("image/1001/background");

        Response<?> response = fileService.uploadBackground(file);

        assertNotNull(response);
        assertEquals("image/1001/background", response.getData());
        verify(fileStrategy).uploadBackground(file, "eaqb");
    }

    @Test
    void getExcelDownloadUrl_shouldDelegateByObjectKey() {
        LoginUserContextHolder.setUserId(1001L);
        when(fileStrategy.getPresignedUrl("eaqb", "excel/1001/9001.xlsx", java.time.Duration.ofMinutes(10)))
                .thenReturn("http://oss/presigned");

        Response<String> response = fileService.getExcelDownloadUrl("excel/1001/9001.xlsx");

        assertEquals("http://oss/presigned", response.getData());
        verify(fileStrategy).getPresignedUrl("eaqb", "excel/1001/9001.xlsx", java.time.Duration.ofMinutes(10));
    }

    @Test
    void getExcelDownloadUrl_otherUserObjectKey_shouldThrowBizException() {
        LoginUserContextHolder.setUserId(1001L);

        BizException ex = assertThrows(BizException.class,
                () -> fileService.getExcelDownloadUrl("excel/1002/9001.xlsx"));

        assertEquals(ResponseCodeEnum.OBJECT_KEY_ACCESS_DENIED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void getExcelDownloadUrl_imageObjectKey_shouldThrowBizException() {
        LoginUserContextHolder.setUserId(1001L);

        BizException ex = assertThrows(BizException.class,
                () -> fileService.getExcelDownloadUrl("image/1001/avatar"));

        assertEquals(ResponseCodeEnum.OBJECT_KEY_ACCESS_DENIED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void getExcelDownloadUrl_noLoginUser_shouldThrowBizException() {
        BizException ex = assertThrows(BizException.class,
                () -> fileService.getExcelDownloadUrl("excel/1001/9001.xlsx"));

        assertEquals(ResponseCodeEnum.OBJECT_KEY_ACCESS_DENIED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void getExcelDownloadUrl_pathTraversalObjectKey_shouldThrowBizException() {
        LoginUserContextHolder.setUserId(1001L);

        BizException ex = assertThrows(BizException.class,
                () -> fileService.getExcelDownloadUrl("excel/1001/../9001.xlsx"));

        assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void getExcelDownloadUrl_doubleSlashObjectKey_shouldThrowBizException() {
        LoginUserContextHolder.setUserId(1001L);

        BizException ex = assertThrows(BizException.class,
                () -> fileService.getExcelDownloadUrl("excel/1001//9001.xlsx"));

        assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void getExcelDownloadUrl_encodedObjectKey_shouldThrowBizException() {
        LoginUserContextHolder.setUserId(1001L);

        BizException ex = assertThrows(BizException.class,
                () -> fileService.getExcelDownloadUrl("excel/1001/%2e%2e/9001.xlsx"));

        assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void getImageViewUrl_shouldDelegateByObjectKey() {
        LoginUserContextHolder.setUserId(1001L);
        when(fileStrategy.getPresignedUrl("eaqb", "image/1001/avatar", java.time.Duration.ofHours(24)))
                .thenReturn("http://oss/image-view-url");

        Response<String> response = fileService.getImageViewUrl("image/1001/avatar");

        assertEquals("http://oss/image-view-url", response.getData());
        verify(fileStrategy).getPresignedUrl("eaqb", "image/1001/avatar", java.time.Duration.ofHours(24));
    }

    @Test
    void getImageViewUrl_otherUserObjectKey_shouldThrowBizException() {
        LoginUserContextHolder.setUserId(1001L);

        BizException ex = assertThrows(BizException.class,
                () -> fileService.getImageViewUrl("image/1002/avatar"));

        assertEquals(ResponseCodeEnum.OBJECT_KEY_ACCESS_DENIED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void getImageViewUrl_excelObjectKey_shouldThrowBizException() {
        LoginUserContextHolder.setUserId(1001L);

        BizException ex = assertThrows(BizException.class,
                () -> fileService.getImageViewUrl("excel/1001/9001.xlsx"));

        assertEquals(ResponseCodeEnum.OBJECT_KEY_ACCESS_DENIED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void getImageViewUrl_noLoginUser_shouldThrowBizException() {
        BizException ex = assertThrows(BizException.class,
                () -> fileService.getImageViewUrl("image/1001/avatar"));

        assertEquals(ResponseCodeEnum.OBJECT_KEY_ACCESS_DENIED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void getImageViewUrl_pathAnomalyObjectKey_shouldThrowBizException() {
        LoginUserContextHolder.setUserId(1001L);

        BizException ex = assertThrows(BizException.class,
                () -> fileService.getImageViewUrl("image/1001//avatar"));

        assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void getImageViewUrl_blankObjectKey_shouldThrowBizException() {
        BizException ex = assertThrows(BizException.class, () -> fileService.getImageViewUrl("  "));

        assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), ex.getErrorCode());
    }
}
