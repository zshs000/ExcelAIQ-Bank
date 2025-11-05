package com.zhoushuo.eaqb.oss.biz.service;

import com.zhoushuo.framework.commono.response.Response;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    /**
     * 上传文件
     *
     * @param file
     * @return
     */
    Response<?> uploadFile(MultipartFile file);

    Response<String> getShortUrl(String filePath);
}