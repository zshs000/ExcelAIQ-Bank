package com.zhoushuo.eaqb.oss.biz.constant;

public final class ObjectPathConstants {

    public static final String AVATAR_OBJECT_NAME = "avatar";
    public static final String BACKGROUND_OBJECT_NAME = "background";

    private ObjectPathConstants() {
    }

    public static String buildExcelObjectKey(Long userId, String objectName) {
        return buildExcelUserPathPrefix(userId) + objectName;
    }

    public static String buildAvatarObjectKey(Long userId) {
        return buildImageUserPathPrefix(userId) + AVATAR_OBJECT_NAME;
    }

    public static String buildBackgroundObjectKey(Long userId) {
        return buildImageUserPathPrefix(userId) + BACKGROUND_OBJECT_NAME;
    }

    public static String buildExcelUserPathPrefix(Long userId) {
        return FileConstants.EXCEL_PATH_PREFIX + userId + "/";
    }

    public static String buildImageUserPathPrefix(Long userId) {
        return FileConstants.IMAGE_PATH_PREFIX + userId + "/";
    }
}
