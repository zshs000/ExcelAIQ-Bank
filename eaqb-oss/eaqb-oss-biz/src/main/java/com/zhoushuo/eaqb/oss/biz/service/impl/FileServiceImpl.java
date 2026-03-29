package com.zhoushuo.eaqb.oss.biz.service.impl;

import com.zhoushuo.eaqb.oss.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.oss.biz.service.FileService;
import com.zhoushuo.eaqb.oss.biz.strategy.FileStrategy;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class FileServiceImpl implements FileService {

    @Resource
    private FileStrategy fileStrategy;

    private static final String BUCKET_NAME = "eaqb";

    @Override
    public Response<?> uploadExcel(MultipartFile file, String objectName) {
        if (StringUtils.isBlank(objectName)) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }
        String objectKey = fileStrategy.uploadExcel(file, BUCKET_NAME, objectName);
        return Response.success(objectKey);
    }

    @Override
    public Response<?> uploadAvatar(MultipartFile file) {
        String url = fileStrategy.uploadAvatar(file, BUCKET_NAME);
        return Response.success(url);
    }

    @Override
    public Response<?> uploadBackground(MultipartFile file) {
        String url = fileStrategy.uploadBackground(file, BUCKET_NAME);
        return Response.success(url);
    }

    @Override
    public Response<String> getPresignedDownloadUrl(String objectKey) {
        log.info("准备生成文件下载访问凭证：{}", objectKey);
        if (StringUtils.isBlank(objectKey)) {
            return Response.fail("对象路径不能为空");
        }

        String accessUrl = fileStrategy.getPresignedDownloadUrl(BUCKET_NAME, objectKey.trim());
        log.info("文件下载访问凭证生成成功：{}", accessUrl);
        return Response.success(accessUrl);
    }
}
