package com.zhoushuo.eaqb.oss.biz.strategy.impl;

import com.zhoushuo.eaqb.oss.biz.config.MinioProperties;
import com.zhoushuo.eaqb.oss.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.oss.biz.strategy.FileStrategy;
import com.zhoushuo.eaqb.oss.biz.util.FileTypeUtil;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.exception.BizException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.*;
import io.minio.http.Method;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MinioFileStrategy implements FileStrategy {

    @Resource
    private MinioProperties minioProperties;

    @Resource
    private MinioClient minioClient;

    @Override
    @SneakyThrows
    public String uploadFile(MultipartFile file, String bucketName) {
        log.info("## 上传文件至 Minio ...");

//        // 判断文件是否为空
//        if (file == null || file.getSize() == 0) {
//            log.error("==> 上传文件异常：文件大小为空 ...");
//            throw new RuntimeException("文件大小不能为空");
//        }
//
//        // 文件的原始名称
//        String originalFileName = file.getOriginalFilename();
//        // 文件的 Content-Type
       String contentType = file.getContentType();
//
//        // 生成存储对象的名称（将 UUID 字符串中的 - 替换成空字符串）
//        String key = UUID.randomUUID().toString().replace("-", "");
//        // 获取文件的后缀，如 .jpg
//        String suffix = originalFileName.substring(originalFileName.lastIndexOf("."));
//
//        // 拼接上文件后缀，即为要存储的文件名
//        String objectName = String.format("%s%s", key, suffix);
        // 判断文件是否为空
        if (file == null || file.getSize() == 0|| file.isEmpty()) {
            log.error("==> 上传文件异常：文件大小为空 ...");
            throw new BizException(ResponseCodeEnum.FILE_EMPTY_ERROR);
        }
        // 1. 自动判断文件类型
        String fileType = FileTypeUtil.getFileType(file);

        //获取当前用户 ID
        Long userId = LoginUserContextHolder.getUserId();


        // 2. 根据文件类型构建路径前缀
        String pathPrefix;
        if ("avatar".equals(fileType)) {
            pathPrefix = "avatar/" + userId + "/";
        } else if ("excel".equals(fileType)) {
            pathPrefix = "excel/" + userId + "/";
        } else {
            // 抛出自定义业务异常:文件类型错误
            throw new BizException(ResponseCodeEnum.FILE_TYPE_ERROR);
        }

        // 3. 生成文件名
        //原始文件名
        String originalFilename = file.getOriginalFilename();
        //后缀
        String extension = FileTypeUtil.getFileExtension(originalFilename);
        //合成
        String newFilename = UUID.randomUUID().toString().replace("-", "") + "." + extension;

        // 4. 完整文件路径
        String objectName = pathPrefix + newFilename;
        log.info("==> 完整文件路径: {}", objectName);


        log.info("==> 开始上传文件至 Minio, ObjectName: {}", objectName);

        // 上传文件至 Minio
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(contentType)
                .build());

//        // 2. 生成一个有效期 7 天的预签名下载 URL (GET 方法)
//        String downloadUrl = minioClient.getPresignedObjectUrl(
//                GetPresignedObjectUrlArgs.builder()
//                        .method(Method.GET) // 指定是用于下载 (GET)
//                        .bucket(bucketName)
//                        .object(objectName) // 使用同一个 objectName
//                        .expiry(7, TimeUnit.DAYS) // 设置链接有效期，例如 7 天
//                        .build());

        // 返回文件的访问链接
        String url = String.format("%s/%s/%s", minioProperties.getEndpoint(), bucketName, objectName);
        log.info("==> 上传文件至 Minio 成功，访问路径: {}", url);
        return url;
    }

    @Override
    public String getShortUrl(String bucketName, String objectName)  {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(7, TimeUnit.DAYS)
                            .build());
        } catch (Exception e) {
            log.error("生成预签名URL失败，bucketName={}, objectName={}", bucketName, objectName, e);
            throw new BizException(ResponseCodeEnum.MINIO_URL_GENERATE_ERROR);
        }
    }
}