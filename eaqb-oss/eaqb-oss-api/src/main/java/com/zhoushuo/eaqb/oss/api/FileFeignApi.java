package com.zhoushuo.eaqb.oss.api;

import com.zhoushuo.eaqb.oss.config.FeignFormConfig;
import com.zhoushuo.eaqb.oss.constant.ApiConstants;
import com.zhoushuo.framework.common.response.Response;
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
     * 上传 Excel 文件。
     *
     * @param file       Excel 文件
     * @param objectName 业务方决定的对象名，如 {@code 9001.xlsx}，策略层会拼成完整 objectKey
     * @return 完整 objectKey，如 {@code excel/123/9001.xlsx}
     */
    @PostMapping(value = PREFIX + "/upload/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Response<?> uploadExcel(@RequestPart(value = "file") MultipartFile file,
                            @RequestPart(value = "objectName") String objectName);

    /**
     * 上传用户头像，路径固定为 {@code image/{userId}/avatar}。
     *
     * @param file 头像图片文件
     * @return 完整 objectKey，如 {@code image/123/avatar}
     */
    @PostMapping(value = PREFIX + "/upload/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Response<?> uploadAvatar(@RequestPart(value = "file") MultipartFile file);

    /**
     * 上传用户背景图，路径固定为 {@code image/{userId}/background}。
     *
     * @param file 背景图片文件
     * @return 完整 objectKey，如 {@code image/123/background}
     */
    @PostMapping(value = PREFIX + "/upload/background", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Response<?> uploadBackground(@RequestPart(value = "file") MultipartFile file);

    /**
     * 根据对象 key 生成 Excel 下载访问凭证。
     *
     * @param objectKey 对象存储中的完整对象路径，如 excel/123/9001.xlsx
     * @return 预签名下载 URL
     */
    @PostMapping(value = PREFIX + "/excel-download-url", consumes = MediaType.TEXT_PLAIN_VALUE)
    Response<String> getExcelDownloadUrl(@RequestBody String objectKey);

    /**
     * 根据对象 key 生成图片查看访问凭证。
     *
     * @param objectKey 对象存储中的完整对象路径，如 image/123/avatar
     * @return 预签名查看 URL
     */
    @PostMapping(value = PREFIX + "/image-view-url", consumes = MediaType.TEXT_PLAIN_VALUE)
    Response<String> getImageViewUrl(@RequestBody String objectKey);

}
