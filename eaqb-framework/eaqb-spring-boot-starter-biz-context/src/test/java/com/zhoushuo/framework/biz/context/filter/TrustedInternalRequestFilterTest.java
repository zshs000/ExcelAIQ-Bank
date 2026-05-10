package com.zhoushuo.framework.biz.context.filter;

import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.common.constant.GlobalConstants;
import com.zhoushuo.framework.common.util.InternalRequestAuthUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TrustedInternalRequestFilterTest {
    private final TrustedInternalRequestFilter filter = new TrustedInternalRequestFilter("secret", 300);

    @AfterEach
    void tearDown() {
        LoginUserContextHolder.remove();
    }

    @Test
    void shouldRejectUnsignedDirectRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        Assertions.assertEquals(401, response.getStatus());
        Assertions.assertTrue(response.getContentAsString().contains("非法内部调用"));
    }

    @Test
    void shouldAcceptSignedInternalRequestAndClearContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        long timestamp = System.currentTimeMillis();
        String signature = InternalRequestAuthUtil.sign("secret", "gateway", "123", timestamp, "GET");
        request.addHeader(GlobalConstants.USER_ID, "123");
        request.addHeader(GlobalConstants.INTERNAL_CALLER_SERVICE, "gateway");
        request.addHeader(GlobalConstants.INTERNAL_CALL_TIMESTAMP, String.valueOf(timestamp));
        request.addHeader(GlobalConstants.INTERNAL_CALL_SIGNATURE, signature);

        filter.doFilter(request, response, chain);

        Assertions.assertSame(request, chain.getRequest());
        Assertions.assertNull(LoginUserContextHolder.getUserId());
    }
}
