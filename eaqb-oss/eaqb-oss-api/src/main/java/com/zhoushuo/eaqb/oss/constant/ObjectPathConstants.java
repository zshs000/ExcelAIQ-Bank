package com.zhoushuo.eaqb.oss.constant;

public final class ObjectPathConstants {

    public static final String EXCEL_PATH_PREFIX = "excel/";
    public static final String IMAGE_PATH_PREFIX = "image/";
    public static final String AVATAR_OBJECT_NAME = "avatar";
    public static final String BACKGROUND_OBJECT_NAME = "background";

    private ObjectPathConstants() {
    }

    public static String buildExcelObjectKey(Long userId, String objectName) {
        return EXCEL_PATH_PREFIX + userId + "/" + objectName;
    }

    public static String buildAvatarObjectKey(Long userId) {
        return IMAGE_PATH_PREFIX + userId + "/" + AVATAR_OBJECT_NAME;
    }

    public static String buildBackgroundObjectKey(Long userId) {
        return IMAGE_PATH_PREFIX + userId + "/" + BACKGROUND_OBJECT_NAME;
    }
}
