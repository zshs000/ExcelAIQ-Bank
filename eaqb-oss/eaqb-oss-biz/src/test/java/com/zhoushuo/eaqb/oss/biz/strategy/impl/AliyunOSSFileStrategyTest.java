package com.zhoushuo.eaqb.oss.biz.strategy.impl;

import com.aliyun.oss.OSS;
import com.zhoushuo.eaqb.oss.biz.config.AliyunOSSProperties;
import com.zhoushuo.eaqb.oss.biz.enums.ResponseCodeEnum;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AliyunOSSFileStrategyTest {

    @Mock
    private OSS ossClient;

    @AfterEach
    void tearDown() {
        LoginUserContextHolder.remove();
    }

    @Test
    void uploadExcel_shouldUseExplicitExcelObjectName() {
        AliyunOSSFileStrategy strategy = new AliyunOSSFileStrategy();
        AliyunOSSProperties properties = new AliyunOSSProperties();
        properties.setEndpoint("http://oss-cn-hangzhou.aliyuncs.com");
        ReflectionTestUtils.setField(strategy, "aliyunOSSProperties", properties);
        ReflectionTestUtils.setField(strategy, "ossClient", ossClient);
        when(ossClient.putObject(any(), any(), any(InputStream.class))).thenReturn(null);
        LoginUserContextHolder.setUserId(1001L);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "fake-excel".getBytes(StandardCharsets.UTF_8)
        );

        String objectKey = strategy.uploadExcel(file, "user-bucket", "9001.xlsx");

        assertEquals("excel/1001/9001.xlsx", objectKey);
        verify(ossClient).putObject(eq("user-bucket"), eq("excel/1001/9001.xlsx"), any(InputStream.class));
    }

    @Test
    void uploadAvatar_shouldUseFixedAvatarObjectName() {
        AliyunOSSFileStrategy strategy = new AliyunOSSFileStrategy();
        AliyunOSSProperties properties = new AliyunOSSProperties();
        properties.setEndpoint("http://oss-cn-hangzhou.aliyuncs.com");
        ReflectionTestUtils.setField(strategy, "aliyunOSSProperties", properties);
        ReflectionTestUtils.setField(strategy, "ossClient", ossClient);
        when(ossClient.putObject(any(), any(), any(InputStream.class))).thenReturn(null);
        LoginUserContextHolder.setUserId(1001L);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "fake-image".getBytes(StandardCharsets.UTF_8)
        );

        String objectKey = strategy.uploadAvatar(file, "user-bucket");

        assertEquals("image/1001/avatar", objectKey);
        verify(ossClient).putObject(eq("user-bucket"), eq("image/1001/avatar"), any(InputStream.class));
    }

    @Test
    void uploadBackground_shouldUseFixedBackgroundObjectName() {
        AliyunOSSFileStrategy strategy = new AliyunOSSFileStrategy();
        AliyunOSSProperties properties = new AliyunOSSProperties();
        properties.setEndpoint("http://oss-cn-hangzhou.aliyuncs.com");
        ReflectionTestUtils.setField(strategy, "aliyunOSSProperties", properties);
        ReflectionTestUtils.setField(strategy, "ossClient", ossClient);
        when(ossClient.putObject(any(), any(), any(InputStream.class))).thenReturn(null);
        LoginUserContextHolder.setUserId(1001L);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "background.jpg",
                "image/jpeg",
                "fake-image".getBytes(StandardCharsets.UTF_8)
        );

        String objectKey = strategy.uploadBackground(file, "user-bucket");

        assertEquals("image/1001/background", objectKey);
        verify(ossClient).putObject(eq("user-bucket"), eq("image/1001/background"), any(InputStream.class));
    }

    @Test
    void uploadAvatar_unknownFileType_shouldThrowBizException() {
        AliyunOSSFileStrategy strategy = new AliyunOSSFileStrategy();
        AliyunOSSProperties properties = new AliyunOSSProperties();
        properties.setEndpoint("http://oss-cn-hangzhou.aliyuncs.com");
        ReflectionTestUtils.setField(strategy, "aliyunOSSProperties", properties);
        ReflectionTestUtils.setField(strategy, "ossClient", ossClient);
        LoginUserContextHolder.setUserId(1001L);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "archive.zip",
                "application/zip",
                "fake-zip".getBytes(StandardCharsets.UTF_8)
        );

        BizException ex = assertThrows(BizException.class, () -> strategy.uploadAvatar(file, "user-bucket"));

        assertEquals(ResponseCodeEnum.FILE_TYPE_ERROR.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void uploadAvatar_noLoginUser_shouldThrowBizException() {
        AliyunOSSFileStrategy strategy = new AliyunOSSFileStrategy();
        AliyunOSSProperties properties = new AliyunOSSProperties();
        properties.setEndpoint("http://oss-cn-hangzhou.aliyuncs.com");
        ReflectionTestUtils.setField(strategy, "aliyunOSSProperties", properties);
        ReflectionTestUtils.setField(strategy, "ossClient", ossClient);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "fake-image".getBytes(StandardCharsets.UTF_8)
        );

        BizException ex = assertThrows(BizException.class, () -> strategy.uploadAvatar(file, "user-bucket"));

        assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void uploadAvatar_emptyFile_shouldThrowBizException() {
        AliyunOSSFileStrategy strategy = new AliyunOSSFileStrategy();
        AliyunOSSProperties properties = new AliyunOSSProperties();
        properties.setEndpoint("http://oss-cn-hangzhou.aliyuncs.com");
        ReflectionTestUtils.setField(strategy, "aliyunOSSProperties", properties);
        ReflectionTestUtils.setField(strategy, "ossClient", ossClient);
        LoginUserContextHolder.setUserId(1001L);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                new byte[0]
        );

        BizException ex = assertThrows(BizException.class, () -> strategy.uploadAvatar(file, "user-bucket"));

        assertEquals(ResponseCodeEnum.FILE_EMPTY_ERROR.getErrorCode(), ex.getErrorCode());
    }
}
