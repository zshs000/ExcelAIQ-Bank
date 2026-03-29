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

    @PostMapping(value = PREFIX + "/upload/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Response<?> uploadExcel(@RequestPart(value = "file") MultipartFile file,
                            @RequestPart(value = "objectName") String objectName);

    @PostMapping(value = PREFIX + "/upload/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Response<?> uploadAvatar(@RequestPart(value = "file") MultipartFile file);

    @PostMapping(value = PREFIX + "/upload/background", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Response<?> uploadBackground(@RequestPart(value = "file") MultipartFile file);

    /**
     * 根据对象 key 生成限时下载访问凭证。
     *
     * @param objectKey 对象存储中的完整对象路径，如 excel/123/9001.xlsx
     * @return 预签名下载 URL
     */
    @PostMapping(value = PREFIX + "/presigned-download-url", consumes = MediaType.TEXT_PLAIN_VALUE)
    Response<String> getPresignedDownloadUrl(@RequestBody String objectKey);

}
