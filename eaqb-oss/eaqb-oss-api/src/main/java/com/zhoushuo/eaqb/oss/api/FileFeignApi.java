package com.zhoushuo.eaqb.oss.api;

import com.zhoushuo.eaqb.oss.config.FeignFormConfig;
import com.zhoushuo.eaqb.oss.constant.ApiConstants;
import com.zhoushuo.framework.commono.response.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(name = ApiConstants.SERVICE_NAME, configuration = FeignFormConfig.class)
public interface FileFeignApi {

    String PREFIX = "/file";

    /**
     * 文件上传
     *
     * @param file
     * @return
     */
    @PostMapping(value = PREFIX + "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Response<?> uploadFile(@RequestPart(value = "file") MultipartFile file);



    /**
     * 根据文件路径获取短链接（预签名URL）
     *
     * @param filePath OSS中的文件路径
     * @return 预签名URL（短链接）
     */
    @PostMapping(value = PREFIX + "/short-url")
    Response<String> getShortUrl(@RequestBody String filePath);

}