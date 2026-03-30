package com.zhoushuo.eaqb.oss.biz.strategy.impl;

import com.zhoushuo.eaqb.oss.biz.config.MinioProperties;
import com.zhoushuo.eaqb.oss.biz.constant.FileConstants;
import com.zhoushuo.eaqb.oss.biz.constant.ObjectPathConstants;
import com.zhoushuo.eaqb.oss.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.oss.biz.strategy.FileStrategy;
import com.zhoushuo.eaqb.oss.biz.util.FileTypeUtil;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.exception.BizException;
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
public class MinioFileStrategy implements FileStrategy {

    @Resource
    private MinioProperties minioProperties;

    @Resource
    private MinioClient minioClient;

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
    public String getPresignedUrl(String bucketName, String objectKey, Duration expire)  {
        try {
            long seconds = expire.getSeconds();
            int expirySeconds = (int) Math.min(seconds, TimeUnit.DAYS.toSeconds(7)); // MinIO 最大 7 天
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

    /** 校验并上传文件到 MinIO，objectKey 由调用方决定。 */
    @SneakyThrows
    private void uploadByObjectKey(MultipartFile file, String bucketName, String objectKey, String expectedFileType) {
        validateFileNotEmpty(file);
        validateFileType(file, expectedFileType);

        log.info("==> 开始上传文件至 Minio, objectKey: {}", objectKey);
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucketName)
                .object(objectKey)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());
        log.info("==> 上传文件至 Minio 成功, objectKey: {}", objectKey);
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
