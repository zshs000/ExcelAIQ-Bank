package com.zhoushuo.eaqb.oss.biz.controller;

import com.zhoushuo.eaqb.oss.biz.service.FileService;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    @Resource
    private FileService fileService;

    @PostMapping(value = "/upload/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<?> uploadExcel(@RequestPart(value = "file") MultipartFile file,
                                   @RequestPart(value = "objectName") String objectName) {
        log.info("当前用户 ID: {}", LoginUserContextHolder.getUserId());
        return fileService.uploadExcel(file, objectName);
    }

    @PostMapping(value = "/upload/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<?> uploadAvatar(@RequestPart(value = "file") MultipartFile file) {
        log.info("当前用户 ID: {}", LoginUserContextHolder.getUserId());
        return fileService.uploadAvatar(file);
    }

    @PostMapping(value = "/upload/background", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<?> uploadBackground(@RequestPart(value = "file") MultipartFile file) {
        log.info("当前用户 ID: {}", LoginUserContextHolder.getUserId());
        return fileService.uploadBackground(file);
    }

    @PostMapping(value = "/presigned-download-url", consumes = MediaType.TEXT_PLAIN_VALUE)
    public Response<String> getPresignedDownloadUrl(@RequestBody String objectKey) {
        log.info("准备获取文件下载访问凭证, objectKey={}", objectKey);
        return fileService.getPresignedDownloadUrl(objectKey);
    }

}
