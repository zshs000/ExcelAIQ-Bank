package com.zhoushuo.eaqb.oss.biz.strategy.impl;

import com.zhoushuo.eaqb.oss.biz.config.MinioProperties;
import com.zhoushuo.eaqb.oss.biz.enums.ResponseCodeEnum;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.common.exception.BizException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinioFileStrategyTest {

    @Mock
    private MinioClient minioClient;

    @AfterEach
    void tearDown() {
        LoginUserContextHolder.remove();
    }

    @Test
    void uploadExcel_shouldUseExplicitExcelObjectName() throws Exception {
        MinioFileStrategy strategy = new MinioFileStrategy();
        MinioProperties minioProperties = new MinioProperties();
        minioProperties.setEndpoint("http://127.0.0.1:9000");
        ReflectionTestUtils.setField(strategy, "minioProperties", minioProperties);
        ReflectionTestUtils.setField(strategy, "minioClient", minioClient);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);
        LoginUserContextHolder.setUserId(1001L);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "fake-excel".getBytes(StandardCharsets.UTF_8)
        );

        String objectKey = strategy.uploadExcel(file, "user-bucket", "9001.xlsx");

        assertEquals("excel/1001/9001.xlsx", objectKey);
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadAvatar_shouldUseFixedAvatarObjectName() throws Exception {
        MinioFileStrategy strategy = new MinioFileStrategy();
        MinioProperties minioProperties = new MinioProperties();
        minioProperties.setEndpoint("http://127.0.0.1:9000");
        ReflectionTestUtils.setField(strategy, "minioProperties", minioProperties);
        ReflectionTestUtils.setField(strategy, "minioClient", minioClient);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);
        LoginUserContextHolder.setUserId(1001L);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "fake-image".getBytes(StandardCharsets.UTF_8)
        );

        String objectKey = strategy.uploadAvatar(file, "user-bucket");

        assertEquals("image/1001/avatar", objectKey);
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadBackground_shouldUseFixedBackgroundObjectName() throws Exception {
        MinioFileStrategy strategy = new MinioFileStrategy();
        MinioProperties minioProperties = new MinioProperties();
        minioProperties.setEndpoint("http://127.0.0.1:9000");
        ReflectionTestUtils.setField(strategy, "minioProperties", minioProperties);
        ReflectionTestUtils.setField(strategy, "minioClient", minioClient);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);
        LoginUserContextHolder.setUserId(1001L);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "background.jpg",
                "image/jpeg",
                "fake-image".getBytes(StandardCharsets.UTF_8)
        );

        String objectKey = strategy.uploadBackground(file, "user-bucket");

        assertEquals("image/1001/background", objectKey);
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadAvatar_unknownFileType_shouldThrowBizException() {
        MinioFileStrategy strategy = new MinioFileStrategy();
        MinioProperties minioProperties = new MinioProperties();
        minioProperties.setEndpoint("http://127.0.0.1:9000");
        ReflectionTestUtils.setField(strategy, "minioProperties", minioProperties);
        ReflectionTestUtils.setField(strategy, "minioClient", minioClient);
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
}

