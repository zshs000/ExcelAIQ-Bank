package com.zhoushuo.eaqb.user.biz.util;

import com.zhoushuo.eaqb.user.biz.enums.ResponseCodeEnum;
import com.zhoushuo.framework.common.exception.BizException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * 用户图片上传的本地前置校验。
 * 先在 user 服务挡住明显非法文件，避免把头像/背景图的业务校验下沉到 OSS 服务。
 */
public final class ImageUploadValidator {

    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp"
    );

    private ImageUploadValidator() {
    }

    public static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BizException(ResponseCodeEnum.FILE_SIZE_EXCEED);
        }

        String extension = getExtension(file.getOriginalFilename());
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }

        String contentType = StringUtils.trimToEmpty(file.getContentType()).toLowerCase();
        if (!StringUtils.startsWith(contentType, "image/")) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }
    }

    private static String getExtension(String fileName) {
        if (StringUtils.isBlank(fileName) || !fileName.contains(".")) {
            return StringUtils.EMPTY;
        }
        return StringUtils.substringAfterLast(fileName, ".").toLowerCase();
    }
}
