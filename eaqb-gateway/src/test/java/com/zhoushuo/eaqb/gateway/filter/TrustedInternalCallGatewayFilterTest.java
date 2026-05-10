package com.zhoushuo.eaqb.gateway.filter;

import com.zhoushuo.framework.common.constant.GlobalConstants;
import com.zhoushuo.framework.common.util.InternalRequestAuthUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustedInternalCallGatewayFilterTest {

    @Test
    void shouldOverwriteSpoofedHeadersAndSignLoggedInRequest() {
        TrustedInternalCallGatewayFilter filter = new TestableTrustedInternalCallGatewayFilter(123L);
        ReflectionTestUtils.setField(filter, "callerService", "eaqb-gateway");
        ReflectionTestUtils.setField(filter, "internalAuthSecret", "secret");

        MockServerHttpRequest request = MockServerHttpRequest.post("/question-bank/api/question/page")
                .header(GlobalConstants.USER_ID, "999")
                .header(GlobalConstants.INTERNAL_CALLER_SERVICE, "fake-service")
                .header(GlobalConstants.INTERNAL_CALL_TIMESTAMP, "1")
                .header(GlobalConstants.INTERNAL_CALL_SIGNATURE, "fake-signature")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingGatewayFilterChain chain = new CapturingGatewayFilterChain();

        filter.filter(exchange, chain).block();

        ServerWebExchange forwardedExchange = chain.getExchange();
        String callerService = forwardedExchange.getRequest().getHeaders().getFirst(GlobalConstants.INTERNAL_CALLER_SERVICE);
        String timestampHeader = forwardedExchange.getRequest().getHeaders().getFirst(GlobalConstants.INTERNAL_CALL_TIMESTAMP);
        String signature = forwardedExchange.getRequest().getHeaders().getFirst(GlobalConstants.INTERNAL_CALL_SIGNATURE);
        String userId = forwardedExchange.getRequest().getHeaders().getFirst(GlobalConstants.USER_ID);

        assertEquals("eaqb-gateway", callerService);
        assertEquals("123", userId);
        assertNotNull(timestampHeader);
        assertNotNull(signature);
        assertTrue(InternalRequestAuthUtil.verify(
                "secret",
                callerService,
                userId,
                Long.parseLong(timestampHeader),
                "POST",
                signature
        ));
    }

    @Test
    void shouldSignAnonymousRequestWithoutUserIdHeader() {
        TrustedInternalCallGatewayFilter filter = new TestableTrustedInternalCallGatewayFilter(null);
        ReflectionTestUtils.setField(filter, "callerService", "eaqb-gateway");
        ReflectionTestUtils.setField(filter, "internalAuthSecret", "secret");

        MockServerHttpRequest request = MockServerHttpRequest.get("/excel-parser/api/file/list").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingGatewayFilterChain chain = new CapturingGatewayFilterChain();
        filter.filter(exchange, chain).block();

        ServerWebExchange forwardedExchange = chain.getExchange();
        String callerService = forwardedExchange.getRequest().getHeaders().getFirst(GlobalConstants.INTERNAL_CALLER_SERVICE);
        String timestampHeader = forwardedExchange.getRequest().getHeaders().getFirst(GlobalConstants.INTERNAL_CALL_TIMESTAMP);
        String signature = forwardedExchange.getRequest().getHeaders().getFirst(GlobalConstants.INTERNAL_CALL_SIGNATURE);
        String userId = forwardedExchange.getRequest().getHeaders().getFirst(GlobalConstants.USER_ID);

        assertEquals("eaqb-gateway", callerService);
        assertNull(userId);
        assertNotNull(timestampHeader);
        assertNotNull(signature);
        assertTrue(InternalRequestAuthUtil.verify(
                "secret",
                callerService,
                null,
                Long.parseLong(timestampHeader),
                "GET",
                signature
        ));
    }

    private static final class TestableTrustedInternalCallGatewayFilter extends TrustedInternalCallGatewayFilter {
        private final Long userId;

        private TestableTrustedInternalCallGatewayFilter(Long userId) {
            this.userId = userId;
        }

        @Override
        protected Long resolveLoginUserId() {
            return userId;
        }
    }

    private static final class CapturingGatewayFilterChain implements GatewayFilterChain {
        private final AtomicReference<ServerWebExchange> exchangeReference = new AtomicReference<>();

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            exchangeReference.set(exchange);
            return Mono.empty();
        }

        private ServerWebExchange getExchange() {
            return exchangeReference.get();
        }
    }
}
