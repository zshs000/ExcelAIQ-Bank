package com.zhoushuo.eaqb.oss.biz.service;

import com.zhoushuo.framework.commono.response.Response;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    Response<?> uploadExcel(MultipartFile file, String objectName);

    Response<?> uploadAvatar(MultipartFile file);

    Response<?> uploadBackground(MultipartFile file);

    Response<String> getPresignedDownloadUrl(String objectKey);
}
