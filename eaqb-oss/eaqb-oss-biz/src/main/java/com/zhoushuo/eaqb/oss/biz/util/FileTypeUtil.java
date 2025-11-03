package com.zhoushuo.eaqb.oss.biz.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 文件类型解析工具类
 */
public class FileTypeUtil {

    // 支持的图片类型
    private static final Set<String> IMAGE_TYPES = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff"
    ));

    // 支持的Excel类型
    private static final Set<String> EXCEL_TYPES = new HashSet<>(Arrays.asList(
            "xlsx", "xls", "csv"
    ));

    /**
     * 根据文件名或内容判断文件类型
     */
    public static String getFileType(MultipartFile file) {
        // 1. 先从文件名获取后缀
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String extension = getFileExtension(originalFilename).toLowerCase();

            // 2. 根据后缀判断类型
            if (IMAGE_TYPES.contains(extension)) {
                return "image";
            }
            if (EXCEL_TYPES.contains(extension)) {
                return "excel";
            }
        }

//        // 3. 可选：进一步读取文件头部魔数进行验证
//        try {
//            byte[] header = new byte[8];
//            file.getInputStream().read(header, 0, 8);
//            String mimeType = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(header));
//
//            if (mimeType != null) {
//                if (mimeType.startsWith("image/")) {
//                    return "avatar";
//                } else if (mimeType.contains("excel") || mimeType.contains("spreadsheet") || mimeType.contains("csv")) {
//                    return "excel";
//                }
//            }
//        } catch (IOException e) {
//            // 处理异常
//        }

        return "unknown";
    }

    public static String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return filename.substring(lastDotIndex + 1);
        }
        return "";
    }
}