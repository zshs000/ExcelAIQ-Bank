package com.zhoushuo.eaqb.oss.biz.service;

import com.zhoushuo.framework.common.response.Response;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    /**
     * 上传 Excel 文件。
     *
     * @param file       Excel 文件
     * @param objectName 业务方决定的对象名，如 {@code 9001.xlsx}
     * @return 完整 objectKey，如 {@code excel/123/9001.xlsx}
     */
    Response<?> uploadExcel(MultipartFile file, String objectName);

    /**
     * 上传用户头像，路径固定为 {@code image/{userId}/avatar}。
     *
     * @param file 头像图片文件
     * @return 完整 objectKey，如 {@code image/123/avatar}
     */
    Response<?> uploadAvatar(MultipartFile file);

    /**
     * 上传用户背景图，路径固定为 {@code image/{userId}/background}。
     *
     * @param file 背景图片文件
     * @return 完整 objectKey，如 {@code image/123/background}
     */
    Response<?> uploadBackground(MultipartFile file);

    /**
     * 根据 objectKey 生成 Excel 文件的限时下载凭证。
     *
     * @param objectKey 对象存储中的完整路径，如 {@code excel/123/9001.xlsx}
     * @return 预签名下载 URL，过期后不可访问
     */
    Response<String> getExcelDownloadUrl(String objectKey);

    /**
     * 根据 objectKey 生成图片的限时查看凭证，适用于头像、背景图等场景。
     *
     * @param objectKey 对象存储中的完整路径，如 {@code image/123/avatar}
     * @return 预签名查看 URL，过期后不可访问
     */
    Response<String> getImageViewUrl(String objectKey);
}
