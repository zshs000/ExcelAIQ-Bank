package com.zhoushuo.framework.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * 内部调用签名工具：
 * 使用共享密钥对“调用方服务 + 用户ID + 时间戳 + 请求方法”做 HMAC，
 * 下游验签后才信任 userId 请求头。
 */
public final class InternalRequestAuthUtil {
    private static final String HMAC_SHA256 = "HmacSHA256";

    private InternalRequestAuthUtil() {
    }

    public static String sign(String secret, String callerService, String userId,
                              long timestamp, String method) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(nullToEmpty(secret).getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] raw = mac.doFinal(buildPayload(callerService, userId, timestamp, method)
                    .getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("生成内部调用签名失败", e);
        }
    }

    public static boolean verify(String secret, String callerService, String userId,
                                 long timestamp, String method, String signature) {
        String expected = sign(secret, callerService, userId, timestamp, method);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                nullToEmpty(signature).getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isTimestampFresh(long timestamp, long maxSkewSeconds) {
        long maxSkewMillis = TimeUnit.SECONDS.toMillis(maxSkewSeconds);
        return Math.abs(System.currentTimeMillis() - timestamp) <= maxSkewMillis;
    }

    private static String buildPayload(String callerService, String userId, long timestamp, String method) {
        return nullToEmpty(callerService) + "\n"
                + nullToEmpty(userId) + "\n"
                + timestamp + "\n"
                + nullToEmpty(method).toUpperCase();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
