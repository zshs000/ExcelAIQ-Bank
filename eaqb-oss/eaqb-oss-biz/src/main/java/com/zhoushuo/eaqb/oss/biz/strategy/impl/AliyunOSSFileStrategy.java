package com.zhoushuo.eaqb.oss.biz.strategy.impl;

import com.aliyun.oss.OSS;
import com.zhoushuo.eaqb.oss.biz.config.AliyunOSSProperties;
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
import java.util.UUID;


@Slf4j
public class AliyunOSSFileStrategy implements FileStrategy  {

    @Resource
    private AliyunOSSProperties aliyunOSSProperties;

    @Resource
    private OSS ossClient;

    @Override
    @SneakyThrows
    public String uploadFile(MultipartFile file, String bucketName) {
        log.info("## 上传文件至阿里云 OSS ...");

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
        if ("image".equals(fileType)) {
            pathPrefix = "image/" + userId + "/";
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



//        // 文件的原始名称
//        String originalFileName = file.getOriginalFilename();
//
//        // 生成存储对象的名称（将 UUID 字符串中的 - 替换成空字符串）
//        String key = UUID.randomUUID().toString().replace("-", "");
//        // 获取文件的后缀，如 .jpg
//        String suffix = originalFileName.substring(originalFileName.lastIndexOf("."));
//
//        // 拼接上文件后缀，即为要存储的文件名
//        String objectName = String.format("%s%s", key, suffix);

        log.info("==> 开始上传文件至阿里云 OSS, ObjectName: {}", objectName);

        // 上传文件至阿里云 OSS
        ossClient.putObject(bucketName, objectName, new ByteArrayInputStream(file.getInputStream().readAllBytes()));

        // 返回文件的访问链接
        String url = String.format("https://%s.%s/%s", bucketName, aliyunOSSProperties.getEndpoint(), objectName);
        log.info("==> 上传文件至阿里云 OSS 成功，访问路径: {}", url);
        return url;
    }
}