package com.zhoushuo.eaqb.oss.biz.service.impl;

import com.zhoushuo.eaqb.oss.biz.service.FileService;
import com.zhoushuo.eaqb.oss.biz.strategy.FileStrategy;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

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
        log.info("准备生成文件访问链接：{}", filePath);

        ParsedFileLocation location = parseFileLocation(filePath);
        if (location == null) {
            return Response.fail("文件路径格式不正确");
        }

        log.info("准备生成文件访问链接：bucketName: {}, objectName: {}", location.bucketName, location.objectName);

        String accessUrl = fileStrategy.getShortUrl(location.bucketName, location.objectName);
        log.info("文件访问链接生成成功：{}", accessUrl);
        return Response.success(accessUrl);
    }

    private ParsedFileLocation parseFileLocation(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(filePath.trim());
            String host = uri.getHost();
            String rawPath = uri.getPath();
            if (host == null || rawPath == null || rawPath.isBlank()) {
                return null;
            }

            String normalizedPath = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
            if (normalizedPath.isBlank()) {
                return null;
            }

            String[] pathSegments = normalizedPath.split("/");

            if (host.contains("aliyuncs.com")) {
                int bucketEndIndex = host.indexOf('.');
                if (bucketEndIndex <= 0) {
                    return null;
                }
                String bucketName = host.substring(0, bucketEndIndex);
                return new ParsedFileLocation(bucketName, normalizedPath);
            }

            if (pathSegments.length < 2) {
                return null;
            }

            String bucketName = pathSegments[0];
            String objectName = normalizedPath.substring(bucketName.length() + 1);
            return new ParsedFileLocation(bucketName, objectName);
        } catch (Exception e) {
            log.error("解析文件路径失败，filePath={}", filePath, e);
            return null;
        }
    }

    private record ParsedFileLocation(String bucketName, String objectName) {
    }
}
