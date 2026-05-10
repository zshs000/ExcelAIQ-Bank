package com.zhoushuo.framework.biz.context.interceptor;

import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.common.constant.GlobalConstants;
import com.zhoushuo.framework.common.util.InternalRequestAuthUtil;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TrustedFeignRequestInterceptorTest {
    @AfterEach
    void tearDown() {
        LoginUserContextHolder.remove();
    }

    @Test
    void shouldAddTrustedHeadersAndUserId() {
        TrustedFeignRequestInterceptor interceptor = new TrustedFeignRequestInterceptor("service-a", "secret");
        RequestTemplate requestTemplate = new RequestTemplate();
        requestTemplate.method("POST");
        LoginUserContextHolder.setUserId(123L);

        interceptor.apply(requestTemplate);

        String callerService = firstHeader(requestTemplate, GlobalConstants.INTERNAL_CALLER_SERVICE);
        String timestamp = firstHeader(requestTemplate, GlobalConstants.INTERNAL_CALL_TIMESTAMP);
        String signature = firstHeader(requestTemplate, GlobalConstants.INTERNAL_CALL_SIGNATURE);
        String userId = firstHeader(requestTemplate, GlobalConstants.USER_ID);

        Assertions.assertEquals("service-a", callerService);
        Assertions.assertEquals("123", userId);
        Assertions.assertTrue(InternalRequestAuthUtil.verify(
                "secret",
                callerService,
                userId,
                Long.parseLong(timestamp),
                "POST",
                signature
        ));
    }

    private String firstHeader(RequestTemplate requestTemplate, String headerName) {
        return requestTemplate.headers().get(headerName).iterator().next();
    }
}
