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

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<?> uploadFile(@RequestPart(value = "file") MultipartFile file) {
        log.info("当前用户 ID: {}", LoginUserContextHolder.getUserId());

        return fileService.uploadFile(file);
    }

    @PostMapping(value = "/short-url")
    public Response<String> getShortUrl(@RequestBody String filePath) {
        return fileService.getShortUrl(filePath);
    }

}