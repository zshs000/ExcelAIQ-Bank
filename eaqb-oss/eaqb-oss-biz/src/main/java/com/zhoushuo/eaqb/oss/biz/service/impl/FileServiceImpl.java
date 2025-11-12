package com.zhoushuo.eaqb.oss.biz.service.impl;

import com.zhoushuo.eaqb.oss.biz.service.FileService;
import com.zhoushuo.eaqb.oss.biz.strategy.FileStrategy;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class FileServiceImpl implements FileService {

    @Resource
    private FileStrategy fileStrategy;

    private static final String BUCKET_NAME = "eaqb";

    @Override
    public Response<?> uploadFile(MultipartFile file) {
        // 上传文件
        String url = fileStrategy.uploadFile(file, BUCKET_NAME);

        return Response.success(url);
    }

    @Override
    public Response<String> getShortUrl(String filePath) {
        log.info("准备生成短链接：{}", filePath);
        String[] parts = filePath.split("/");
        if (parts.length < 5) {
            return Response.fail("文件路径格式不正确");
        }

        String bucketName = parts[3]; // "eaqb"
        // 从第4部分开始拼接objectName: "excel/{userId}/{uuid}.xlsx"
        StringBuilder objectNameBuilder = new StringBuilder();
        for (int i = 4; i < parts.length; i++) {
            objectNameBuilder.append(parts[i]);
            if (i < parts.length - 1) {
                objectNameBuilder.append("/");
            }
        }

        String objectName = objectNameBuilder.toString();
        log.info("准备生成短链接：bucketName: {}, objectName: {}", bucketName, objectName);

        // 调用策略获取短链接
        String shortUrl = fileStrategy.getShortUrl(bucketName, objectName);
        log.info("短链接生成成功：{}", shortUrl);
        return Response.success(shortUrl);
    }
}