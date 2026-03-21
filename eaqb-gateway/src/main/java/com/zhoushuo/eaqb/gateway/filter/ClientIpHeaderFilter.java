package com.zhoushuo.eaqb.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class ClientIpHeaderFilter implements GlobalFilter {
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_X_REAL_IP = "X-Real-IP";
    private static final String UNKNOWN = "unknown";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String xForwardedFor = headers.getFirst(HEADER_X_FORWARDED_FOR);
        String xRealIp = headers.getFirst(HEADER_X_REAL_IP);

        String clientIp = resolveClientIp(xForwardedFor, xRealIp, exchange);
        if (StringUtils.isBlank(clientIp)) {
            return chain.filter(exchange);
        }

        String forwardedForToSet = StringUtils.isNotBlank(xForwardedFor) ? xForwardedFor : clientIp;
        String realIpToSet = StringUtils.isNotBlank(xRealIp) ? xRealIp : clientIp;

        ServerWebExchange newExchange = exchange.mutate()
                .request(builder -> builder.headers(httpHeaders -> {
                    httpHeaders.set(HEADER_X_FORWARDED_FOR, forwardedForToSet);
                    httpHeaders.set(HEADER_X_REAL_IP, realIpToSet);
                }))
                .build();

        log.debug("透传客户端 IP 到下游, xForwardedFor: {}, xRealIp: {}", forwardedForToSet, realIpToSet);
        return chain.filter(newExchange);
    }

    private String resolveClientIp(String xForwardedFor, String xRealIp, ServerWebExchange exchange) {
        if (isUsableIp(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        if (isUsableIp(xRealIp)) {
            return xRealIp.trim();
        }

        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return null;
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    private boolean isUsableIp(String ip) {
        return StringUtils.isNotBlank(ip) && !UNKNOWN.equalsIgnoreCase(ip.trim());
    }
}
