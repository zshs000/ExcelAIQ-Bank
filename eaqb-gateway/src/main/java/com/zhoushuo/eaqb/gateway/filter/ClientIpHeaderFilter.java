package com.zhoushuo.eaqb.gateway.filter;

import com.google.common.net.InetAddresses;
import com.zhoushuo.eaqb.gateway.config.ClientIpProperties;
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
import java.util.Arrays;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class ClientIpHeaderFilter implements GlobalFilter {
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_X_REAL_IP = "X-Real-IP";
    private static final String UNKNOWN = "unknown";

    private final ClientIpProperties clientIpProperties;

    public ClientIpHeaderFilter(ClientIpProperties clientIpProperties) {
        this.clientIpProperties = clientIpProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String xForwardedFor = headers.getFirst(HEADER_X_FORWARDED_FOR);
        String xRealIp = headers.getFirst(HEADER_X_REAL_IP);

        String clientIp = resolveClientIp(xForwardedFor, xRealIp, exchange);
        if (StringUtils.isBlank(clientIp)) {
            return chain.filter(exchange);
        }

        ServerWebExchange newExchange = exchange.mutate()
                .request(builder -> builder.headers(httpHeaders -> {
                    httpHeaders.set(HEADER_X_FORWARDED_FOR, clientIp);
                    httpHeaders.set(HEADER_X_REAL_IP, clientIp);
                }))
                .build();

        log.debug("透传客户端 IP 到下游, clientIp: {}, xForwardedFor: {}, xRealIp: {}, remoteAddr: {}",
                clientIp, xForwardedFor, xRealIp, exchange.getRequest().getRemoteAddress());
        return chain.filter(newExchange);
    }

    private String resolveClientIp(String xForwardedFor, String xRealIp, ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return null;
        }

        String directRemoteIp = normalizeIp(remoteAddress.getAddress().getHostAddress());
        if (!isTrustedProxy(directRemoteIp)) {
            return directRemoteIp;
        }
        if (!isUsableIp(xForwardedFor)) {
            return isValidIp(xRealIp) ? normalizeIp(xRealIp.trim()) : directRemoteIp;
        }

        List<String> forwardedIps = Arrays.stream(xForwardedFor.split(","))
                .map(String::trim)
                .filter(this::isValidIp)
                .map(this::normalizeIp)
                .toList();
        for (int i = forwardedIps.size() - 1; i >= 0; i--) {
            String forwardedIp = forwardedIps.get(i);
            if (!isTrustedProxy(forwardedIp)) {
                return forwardedIp;
            }
        }

        return forwardedIps.isEmpty() ? directRemoteIp : forwardedIps.get(0);
    }

    private boolean isUsableIp(String ip) {
        return StringUtils.isNotBlank(ip) && !UNKNOWN.equalsIgnoreCase(ip.trim());
    }

    private boolean isValidIp(String ip) {
        return isUsableIp(ip) && InetAddresses.isInetAddress(ip.trim());
    }

    private String normalizeIp(String ip) {
        if (!isValidIp(ip)) {
            return ip;
        }
        byte[] address = InetAddresses.forString(ip.trim()).getAddress();
        if (isIpv4MappedIpv6Address(address)) {
            return toDottedDecimal(address);
        }
        return InetAddresses.toAddrString(InetAddresses.forString(ip.trim()));
    }

    private boolean isIpv4MappedIpv6Address(byte[] address) {
        if (address.length != 16) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (address[i] != 0) {
                return false;
            }
        }
        return (address[10] & 0xff) == 0xff && (address[11] & 0xff) == 0xff;
    }

    private String toDottedDecimal(byte[] ipv4MappedAddress) {
        return (ipv4MappedAddress[12] & 0xff) + "."
                + (ipv4MappedAddress[13] & 0xff) + "."
                + (ipv4MappedAddress[14] & 0xff) + "."
                + (ipv4MappedAddress[15] & 0xff);
    }

    private boolean isTrustedProxy(String ip) {
        return clientIpProperties.getTrustedProxies().stream()
                .anyMatch(trustedProxy -> matchesTrustedProxy(ip, trustedProxy));
    }

    private boolean matchesTrustedProxy(String ip, String trustedProxy) {
        if (StringUtils.isBlank(ip) || StringUtils.isBlank(trustedProxy)) {
            return false;
        }
        String normalizedTrustedProxy = trustedProxy.trim();
        if (!normalizedTrustedProxy.contains("/")) {
            return normalizeIp(normalizedTrustedProxy).equals(normalizeIp(ip));
        }

        String[] cidrParts = normalizedTrustedProxy.split("/", 2);
        if (!isValidIp(ip)) {
            return false;
        }
        byte[] ipBytes = InetAddresses.forString(ip).getAddress();
        byte[] cidrBytes = InetAddresses.forString(cidrParts[0]).getAddress();
        int prefixLength = Integer.parseInt(cidrParts[1]);
        return isInCidr(ipBytes, cidrBytes, prefixLength);
    }

    private boolean isInCidr(byte[] ipBytes, byte[] cidrBytes, int prefixLength) {
        if (ipBytes.length != cidrBytes.length || prefixLength < 0 || prefixLength > ipBytes.length * 8) {
            return false;
        }

        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (ipBytes[i] != cidrBytes[i]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }

        int mask = (0xff << (8 - remainingBits)) & 0xff;
        return ((ipBytes[fullBytes] & 0xff) & mask) == ((cidrBytes[fullBytes] & 0xff) & mask);
    }
}
