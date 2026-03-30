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

    /**
     * Excel 文件下载预签名 URL。
     */
    @PostMapping(value = "/excel-download-url", consumes = MediaType.TEXT_PLAIN_VALUE)
    public Response<String> getExcelDownloadUrl(@RequestBody String objectKey) {
        log.info("准备获取 Excel 下载访问凭证, objectKey={}", objectKey);
        return fileService.getExcelDownloadUrl(objectKey);
    }

    /**
     * 图片查看预签名 URL。
     */
    @PostMapping(value = "/image-view-url", consumes = MediaType.TEXT_PLAIN_VALUE)
    public Response<String> getImageViewUrl(@RequestBody String objectKey) {
        log.info("准备获取图片查看访问凭证, objectKey={}", objectKey);
        return fileService.getImageViewUrl(objectKey);
    }

}
