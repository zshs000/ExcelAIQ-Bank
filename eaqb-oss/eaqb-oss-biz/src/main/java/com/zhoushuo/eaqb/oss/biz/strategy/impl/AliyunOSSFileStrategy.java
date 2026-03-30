package com.zhoushuo.eaqb.oss.biz.strategy.impl;

import com.aliyun.oss.OSS;
import com.zhoushuo.eaqb.oss.biz.config.AliyunOSSProperties;
import com.zhoushuo.eaqb.oss.biz.constant.FileConstants;
import com.zhoushuo.eaqb.oss.biz.constant.ObjectPathConstants;
import com.zhoushuo.eaqb.oss.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.oss.biz.strategy.FileStrategy;
import com.zhoushuo.eaqb.oss.biz.util.FileTypeUtil;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.exception.BizException;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.TimeUnit;


@Slf4j
public class AliyunOSSFileStrategy implements FileStrategy  {
    private static final long ACCESS_URL_EXPIRE_MILLIS = TimeUnit.DAYS.toMillis(7);

    @Resource
    private AliyunOSSProperties aliyunOSSProperties;

    @Resource
    private OSS ossClient;

    @Override
    @SneakyThrows
    public String uploadExcel(MultipartFile file, String bucketName, String objectName) {
        String objectKey = ObjectPathConstants.buildExcelObjectKey(currentUserId(), objectName);
        uploadByObjectKey(file, bucketName, objectKey, FileConstants.FILE_TYPE_EXCEL);
        return objectKey;
    }

    @Override
    @SneakyThrows
    public String uploadAvatar(MultipartFile file, String bucketName) {
        String objectKey = ObjectPathConstants.buildAvatarObjectKey(currentUserId());
        uploadByObjectKey(file, bucketName, objectKey, FileConstants.FILE_TYPE_IMAGE);
        return objectKey;
    }

    @Override
    @SneakyThrows
    public String uploadBackground(MultipartFile file, String bucketName) {
        String objectKey = ObjectPathConstants.buildBackgroundObjectKey(currentUserId());
        uploadByObjectKey(file, bucketName, objectKey, FileConstants.FILE_TYPE_IMAGE);
        return objectKey;
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

    /** 校验并上传文件到阿里云 OSS，objectKey 由调用方决定。 */
    @SneakyThrows
    private void uploadByObjectKey(MultipartFile file, String bucketName, String objectKey, String expectedFileType) {
        validateFileNotEmpty(file);
        validateFileType(file, expectedFileType);

        log.info("==> 开始上传文件至阿里云 OSS, objectKey: {}", objectKey);
        ossClient.putObject(bucketName, objectKey, new ByteArrayInputStream(file.getInputStream().readAllBytes()));
        log.info("==> 上传文件至阿里云 OSS 成功, objectKey: {}", objectKey);
    }

    /** 校验文件不为空，为空则抛出业务异常。 */
    private void validateFileNotEmpty(MultipartFile file) {
        if (file == null || file.getSize() == 0 || file.isEmpty()) {
            log.error("==> 上传文件异常：文件大小为空 ...");
            throw new BizException(ResponseCodeEnum.FILE_EMPTY_ERROR);
        }
    }

    /** 校验文件类型是否符合预期，不符合则抛出业务异常。 */
    private void validateFileType(MultipartFile file, String expectedFileType) {
        String fileType = FileTypeUtil.getFileType(file);
        if (!expectedFileType.equals(fileType)) {
            throw new BizException(ResponseCodeEnum.FILE_TYPE_ERROR);
        }
    }

    /** 从登录上下文中取当前用户 ID，未登录则抛出业务异常。 */
    private Long currentUserId() {
        Long userId = LoginUserContextHolder.getUserId();
        if (userId == null) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }
        return userId;
    }
}
