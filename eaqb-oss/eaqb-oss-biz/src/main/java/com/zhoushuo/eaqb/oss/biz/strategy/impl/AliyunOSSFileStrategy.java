package com.zhoushuo.eaqb.oss.biz.strategy.impl;

import com.aliyun.oss.OSS;
import com.zhoushuo.eaqb.oss.biz.config.AliyunOSSProperties;
import com.zhoushuo.eaqb.oss.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.oss.biz.strategy.AbstractFileStrategy;
import com.zhoushuo.framework.commono.exception.BizException;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.net.URL;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AliyunOSSFileStrategy extends AbstractFileStrategy {

    private static final long ACCESS_URL_EXPIRE_MILLIS = TimeUnit.DAYS.toMillis(7);

    @Resource
    private AliyunOSSProperties aliyunOSSProperties;

    @Resource
    private OSS ossClient;

    @Override
    @SneakyThrows
    protected void doUploadByObjectKey(MultipartFile file, String bucketName, String objectKey) {
        log.info("==> 开始上传文件至阿里云 OSS, objectKey: {}", objectKey);
        ossClient.putObject(bucketName, objectKey, file.getInputStream());
        log.info("==> 上传文件至阿里云 OSS 成功, objectKey: {}", objectKey);
    }

    @Override
    public String getPresignedUrl(String bucketName, String objectKey, Duration expire) {
        try {
            long millis = expire.toMillis();
            long effective = Math.min(millis, ACCESS_URL_EXPIRE_MILLIS);
            Date expiration = new Date(System.currentTimeMillis() + effective);
            URL presignedUrl = ossClient.generatePresignedUrl(bucketName, objectKey, expiration);
            return presignedUrl.toString();
        } catch (Exception e) {
            log.error("生成阿里云 OSS 预签名下载URL失败，bucketName={}, objectKey={}", bucketName, objectKey, e);
            throw new BizException(ResponseCodeEnum.FILE_ACCESS_URL_GENERATE_ERROR);
        }
    }
}
