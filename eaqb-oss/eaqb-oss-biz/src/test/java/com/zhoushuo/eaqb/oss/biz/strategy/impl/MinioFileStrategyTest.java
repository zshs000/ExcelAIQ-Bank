package com.zhoushuo.eaqb.oss.biz.strategy.impl;

import com.zhoushuo.eaqb.oss.biz.config.MinioProperties;
import com.zhoushuo.eaqb.oss.biz.enums.ResponseCodeEnum;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.exception.BizException;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void uploadFile_imageFile_shouldUseImagePrefix() throws Exception {
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

        String url = strategy.uploadFile(file, "user-bucket");

        assertTrue(url.startsWith("http://127.0.0.1:9000/user-bucket/image/1001/"));
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadFile_unknownFileType_shouldThrowBizException() {
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

        BizException ex = assertThrows(BizException.class, () -> strategy.uploadFile(file, "user-bucket"));

        assertEquals(ResponseCodeEnum.FILE_TYPE_ERROR.getErrorCode(), ex.getErrorCode());
    }
}
