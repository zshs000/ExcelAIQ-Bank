package com.zhoushuo.eaqb.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpHeaderFilterTest {

    private final ClientIpHeaderFilter filter = new ClientIpHeaderFilter();

    @Test
    void shouldKeepExistingForwardedHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/verification/code/send")
                .header("X-Forwarded-For", "1.1.1.1, 10.0.0.1")
                .header("X-Real-IP", "1.1.1.1")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingGatewayFilterChain chain = new CapturingGatewayFilterChain();

        filter.filter(exchange, chain).block();

        ServerWebExchange forwardedExchange = chain.getExchange();
        assertEquals("1.1.1.1, 10.0.0.1", forwardedExchange.getRequest().getHeaders().getFirst("X-Forwarded-For"));
        assertEquals("1.1.1.1", forwardedExchange.getRequest().getHeaders().getFirst("X-Real-IP"));
    }

    @Test
    void shouldPopulateForwardedHeadersFromRemoteAddress() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/verification/code/send")
                .remoteAddress(new InetSocketAddress("192.168.1.100", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingGatewayFilterChain chain = new CapturingGatewayFilterChain();

        filter.filter(exchange, chain).block();

        ServerWebExchange forwardedExchange = chain.getExchange();
        assertEquals("192.168.1.100", forwardedExchange.getRequest().getHeaders().getFirst("X-Forwarded-For"));
        assertEquals("192.168.1.100", forwardedExchange.getRequest().getHeaders().getFirst("X-Real-IP"));
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
