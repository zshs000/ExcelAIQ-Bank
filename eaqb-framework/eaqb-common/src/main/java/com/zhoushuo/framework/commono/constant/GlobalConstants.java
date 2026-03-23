package com.zhoushuo.framework.commono.constant;

public interface GlobalConstants {

    /**
     * 用户 ID
     */
    String USER_ID = "userId";

    /**
     * 内部调用方服务名
     */
    String INTERNAL_CALLER_SERVICE = "X-Caller-Service";

    /**
     * 内部调用时间戳（毫秒）
     */
    String INTERNAL_CALL_TIMESTAMP = "X-Call-Timestamp";

    /**
     * 内部调用签名
     */
    String INTERNAL_CALL_SIGNATURE = "X-Call-Signature";
}
