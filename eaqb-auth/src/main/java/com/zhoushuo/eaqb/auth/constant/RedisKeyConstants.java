package com.zhoushuo.eaqb.auth.constant;

public class RedisKeyConstants {
    private static final String LOGIN_VERIFICATION_CODE_SCENE = "login:";
    private static final String PASSWORD_UPDATE_VERIFICATION_CODE_SCENE = "password_update:";

    /**
     * 验证码 KEY 前缀
     */
    private static final String VERIFICATION_CODE_KEY_PREFIX = "verification_code:";

    /**
     * 验证码每日发送次数 KEY 前缀
     */
    private static final String VERIFICATION_CODE_DAILY_COUNT_KEY_PREFIX = "verification_code:daily:";

    /**
     * IP 每日发送验证码次数 KEY 前缀
     */
    private static final String VERIFICATION_CODE_IP_DAILY_COUNT_KEY_PREFIX = "verification_code:ip:daily:";

    /**
     * 验证码黑名单 KEY 前缀
     */
    private static final String VERIFICATION_CODE_BLACKLIST_KEY_PREFIX = "verification_code:blacklist:";

    /**
     * 小哈书全局 ID 生成器 KEY
     */
    public static final String EAQB_ID_GENERATOR_KEY = "eaqb.id.generator";


    /**
     * 用户角色数据 KEY 前缀
     */
    private static final String USER_ROLES_KEY_PREFIX = "user:roles:";


    /**
     * 角色对应的权限集合 KEY 前缀
     */
    private static final String ROLE_PERMISSIONS_KEY_PREFIX = "role:permissions:";

    /**
     * 构建验证码 KEY
     * @param phone
     * @return
     */
    public static String buildLoginVerificationCodeKey(String phone) {
        return VERIFICATION_CODE_KEY_PREFIX + LOGIN_VERIFICATION_CODE_SCENE + phone;
    }

    /**
     * 构建修改密码验证码 KEY
     *
     * @param phone
     * @return
     */
    public static String buildPasswordUpdateVerificationCodeKey(String phone) {
        return VERIFICATION_CODE_KEY_PREFIX + PASSWORD_UPDATE_VERIFICATION_CODE_SCENE + phone;
    }

    /**
     * 构建验证码每日发送次数 KEY
     * @param phone
     * @return
     */
    public static String buildVerificationCodeDailyCountKey(String phone) {
        return VERIFICATION_CODE_DAILY_COUNT_KEY_PREFIX + phone;
    }

    /**
     * 构建 IP 每日发送验证码次数 KEY
     * @param ip
     * @return
     */
    public static String buildVerificationCodeIpDailyCountKey(String ip) {
        return VERIFICATION_CODE_IP_DAILY_COUNT_KEY_PREFIX + ip;
    }

    /**
     * 构建验证码黑名单 KEY
     * @param phone
     * @return
     */
    public static String buildVerificationCodeBlacklistKey(String phone) {
        return VERIFICATION_CODE_BLACKLIST_KEY_PREFIX + phone;
    }

    /**
     * 用户对应的角色集合 KEY
     * @param userId
     * @return
     */
    public static String buildUserRoleKey(Long userId) {
        return USER_ROLES_KEY_PREFIX + userId;
    }


    /**
     * 构建角色对应的权限集合 KEY
     * @param roleKey
     * @return
     */
    public static String buildRolePermissionsKey(String roleKey) {
        return ROLE_PERMISSIONS_KEY_PREFIX + roleKey;
    }
}
