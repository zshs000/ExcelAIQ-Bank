package com.zhoushuo.framework.biz.context.interceptor;

import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.constant.GlobalConstants;
import com.zhoushuo.framework.commono.util.InternalRequestAuthUtil;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TrustedFeignRequestInterceptor implements RequestInterceptor {
    private final String callerService;
    private final String internalAuthSecret;

    public TrustedFeignRequestInterceptor(String callerService, String internalAuthSecret) {
        this.callerService = callerService;
        this.internalAuthSecret = internalAuthSecret;
    }

    @Override
    public void apply(RequestTemplate requestTemplate) {
        Long userId = LoginUserContextHolder.getUserId();
        String userIdHeader = userId == null ? null : String.valueOf(userId);
        long timestamp = System.currentTimeMillis();
        String method = requestTemplate.method();
        String signature = InternalRequestAuthUtil.sign(
                internalAuthSecret,
                callerService,
                userIdHeader,
                timestamp,
                method
        );

        requestTemplate.header(GlobalConstants.USER_ID);
        requestTemplate.header(GlobalConstants.INTERNAL_CALLER_SERVICE);
        requestTemplate.header(GlobalConstants.INTERNAL_CALL_TIMESTAMP);
        requestTemplate.header(GlobalConstants.INTERNAL_CALL_SIGNATURE);
        requestTemplate.header(GlobalConstants.INTERNAL_CALLER_SERVICE, callerService);
        requestTemplate.header(GlobalConstants.INTERNAL_CALL_TIMESTAMP, String.valueOf(timestamp));
        requestTemplate.header(GlobalConstants.INTERNAL_CALL_SIGNATURE, signature);

        if (userIdHeader != null) {
            requestTemplate.header(GlobalConstants.USER_ID, userIdHeader);
            log.debug("feign 请求设置受信 userId: {}", userIdHeader);
        }
    }
}
