package com.zhoushuo.eaqb.oss.biz.strategy.impl;

import com.zhoushuo.eaqb.oss.biz.config.MinioProperties;
import com.zhoushuo.eaqb.oss.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.oss.biz.strategy.AbstractFileStrategy;
import com.zhoushuo.framework.common.exception.BizException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MinioFileStrategy extends AbstractFileStrategy {

    @Resource
    private MinioProperties minioProperties;

    @Resource
    private MinioClient minioClient;

    @Override
    @SneakyThrows
    protected void doUploadByObjectKey(MultipartFile file, String bucketName, String objectKey) {
        log.info("==> 开始上传文件至 Minio, objectKey: {}", objectKey);
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucketName)
                .object(objectKey)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());
        log.info("==> 上传文件至 Minio 成功, objectKey: {}", objectKey);
    }

    @Override
    public String getPresignedUrl(String bucketName, String objectKey, Duration expire) {
        try {
            long seconds = expire.getSeconds();
            int expirySeconds = (int) Math.min(seconds, TimeUnit.DAYS.toSeconds(7));
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            log.error("生成预签名下载URL失败，bucketName={}, objectKey={}", bucketName, objectKey, e);
            throw new BizException(ResponseCodeEnum.FILE_ACCESS_URL_GENERATE_ERROR);
        }
    }
}
