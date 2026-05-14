package com.zhoushuo.eaqb.gateway.filter;

import com.zhoushuo.eaqb.gateway.config.ClientIpProperties;
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

    @Test
    void shouldIgnoreForwardedHeadersFromUntrustedRemoteAddress() {
        ClientIpHeaderFilter filter = new ClientIpHeaderFilter(new ClientIpProperties());
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/verification/code/send")
                .header("X-Forwarded-For", "6.6.6.6")
                .header("X-Real-IP", "6.6.6.6")
                .remoteAddress(new InetSocketAddress("203.0.113.10", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingGatewayFilterChain chain = new CapturingGatewayFilterChain();

        filter.filter(exchange, chain).block();

        ServerWebExchange forwardedExchange = chain.getExchange();
        assertEquals("203.0.113.10", forwardedExchange.getRequest().getHeaders().getFirst("X-Forwarded-For"));
        assertEquals("203.0.113.10", forwardedExchange.getRequest().getHeaders().getFirst("X-Real-IP"));
    }

    @Test
    void shouldPopulateForwardedHeadersFromRemoteAddress() {
        ClientIpHeaderFilter filter = new ClientIpHeaderFilter(new ClientIpProperties());
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

    @Test
    void shouldResolveFirstUntrustedIpFromRightWhenRemoteAddressIsTrustedProxy() {
        ClientIpProperties clientIpProperties = trustedProperties("127.0.0.1", "10.0.0.0/8");
        ClientIpHeaderFilter filter = new ClientIpHeaderFilter(clientIpProperties);
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/verification/code/send")
                .header("X-Forwarded-For", "6.6.6.6, 203.0.113.10, 10.0.0.5")
                .header("X-Real-IP", "6.6.6.6")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingGatewayFilterChain chain = new CapturingGatewayFilterChain();

        filter.filter(exchange, chain).block();

        ServerWebExchange forwardedExchange = chain.getExchange();
        assertEquals("203.0.113.10", forwardedExchange.getRequest().getHeaders().getFirst("X-Forwarded-For"));
        assertEquals("203.0.113.10", forwardedExchange.getRequest().getHeaders().getFirst("X-Real-IP"));
    }

    @Test
    void shouldSkipInvalidForwardedIpValues() {
        ClientIpProperties clientIpProperties = trustedProperties("127.0.0.1", "10.0.0.0/8");
        ClientIpHeaderFilter filter = new ClientIpHeaderFilter(clientIpProperties);
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/verification/code/send")
                .header("X-Forwarded-For", "not-an-ip, 203.0.113.10, 10.0.0.5")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingGatewayFilterChain chain = new CapturingGatewayFilterChain();

        filter.filter(exchange, chain).block();

        ServerWebExchange forwardedExchange = chain.getExchange();
        assertEquals("203.0.113.10", forwardedExchange.getRequest().getHeaders().getFirst("X-Forwarded-For"));
        assertEquals("203.0.113.10", forwardedExchange.getRequest().getHeaders().getFirst("X-Real-IP"));
    }

    @Test
    void shouldUseRealIpWhenTrustedProxyDoesNotSendForwardedFor() {
        ClientIpProperties clientIpProperties = trustedProperties("127.0.0.1");
        ClientIpHeaderFilter filter = new ClientIpHeaderFilter(clientIpProperties);
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/verification/code/send")
                .header("X-Real-IP", "203.0.113.10")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingGatewayFilterChain chain = new CapturingGatewayFilterChain();

        filter.filter(exchange, chain).block();

        ServerWebExchange forwardedExchange = chain.getExchange();
        assertEquals("203.0.113.10", forwardedExchange.getRequest().getHeaders().getFirst("X-Forwarded-For"));
        assertEquals("203.0.113.10", forwardedExchange.getRequest().getHeaders().getFirst("X-Real-IP"));
    }

    @Test
    void shouldTrustIpv6LoopbackConfiguredInCompressedFormat() {
        ClientIpProperties clientIpProperties = trustedProperties("::1");
        ClientIpHeaderFilter filter = new ClientIpHeaderFilter(clientIpProperties);
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/verification/code/send")
                .header("X-Forwarded-For", "203.0.113.10")
                .remoteAddress(new InetSocketAddress("::1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingGatewayFilterChain chain = new CapturingGatewayFilterChain();

        filter.filter(exchange, chain).block();

        ServerWebExchange forwardedExchange = chain.getExchange();
        assertEquals("203.0.113.10", forwardedExchange.getRequest().getHeaders().getFirst("X-Forwarded-For"));
        assertEquals("203.0.113.10", forwardedExchange.getRequest().getHeaders().getFirst("X-Real-IP"));
    }

    @Test
    void shouldNormalizeIpv4MappedIpv6Address() {
        ClientIpProperties clientIpProperties = trustedProperties("127.0.0.1");
        ClientIpHeaderFilter filter = new ClientIpHeaderFilter(clientIpProperties);
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/verification/code/send")
                .header("X-Forwarded-For", "::ffff:203.0.113.10")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingGatewayFilterChain chain = new CapturingGatewayFilterChain();

        filter.filter(exchange, chain).block();

        ServerWebExchange forwardedExchange = chain.getExchange();
        assertEquals("203.0.113.10", forwardedExchange.getRequest().getHeaders().getFirst("X-Forwarded-For"));
        assertEquals("203.0.113.10", forwardedExchange.getRequest().getHeaders().getFirst("X-Real-IP"));
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

    private ClientIpProperties trustedProperties(String... trustedProxies) {
        ClientIpProperties clientIpProperties = new ClientIpProperties();
        clientIpProperties.getTrustedProxies().addAll(java.util.Arrays.asList(trustedProxies));
        clientIpProperties.validate();
        return clientIpProperties;
    }
}
