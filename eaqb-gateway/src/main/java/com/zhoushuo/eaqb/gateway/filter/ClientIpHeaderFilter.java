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

    /**
     * 客户端 IP 清洗过滤器。
     * <p>
     * 解决两个问题：
     * 1. 下游服务（如 auth）需要通过 X-Forwarded-For / X-Real-IP 获取客户端 IP
     * 2. 这两个头不可信，客户端可以伪造，必须结合可信代理配置做清洗
     * <p>
     * 核心逻辑：
     * - 如果 remoteAddress 不在可信代理列表中，直接使用 remoteAddress（客户端直连，忽略请求头）
     * - 如果 remoteAddress 是可信代理，从右向左遍历 X-Forwarded-For，取第一个非可信代理的 IP
     *   （右侧是离网关最近的代理，伪造的 IP 通常在左侧）
     * <p>
     * 详细设计文档：docs/auth-gateway/01-Sa-Token与网关真实IP复盘.md
     */
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

    /**
     * 解析真实客户端 IP。
     * <p>
     * X-Forwarded-For 格式：client, proxy1, proxy2（左侧是原始客户端，右侧是离网关最近的代理）
     * 从右向左遍历，跳过可信代理，取第一个非可信代理 IP 即为真实客户端 IP。
     * 不能简单取最左侧，因为客户端可以在进入第一个代理前伪造左侧的值。
     */
    private String resolveClientIp(String xForwardedFor, String xRealIp, ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return null;
        }

        String directRemoteIp = normalizeIp(remoteAddress.getAddress().getHostAddress());
        // 非可信代理直连，说明客户端直接访问网关，忽略请求头中的伪造 IP
        if (!isTrustedProxy(directRemoteIp)) {
            return directRemoteIp;
        }

        // remoteAddress 是可信代理，尝试从 X-Forwarded-For 解析
        if (!isUsableIp(xForwardedFor)) {
            // 没有 X-Forwarded-For，退而使用 X-Real-IP
            return isValidIp(xRealIp) ? normalizeIp(xRealIp.trim()) : directRemoteIp;
        }

        // 从右向左遍历，找到第一个非可信代理的 IP
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

        // 所有 IP 都在可信代理列表中（异常情况），取最左侧
        return forwardedIps.isEmpty() ? directRemoteIp : forwardedIps.get(0);
    }

    private boolean isUsableIp(String ip) {
        return StringUtils.isNotBlank(ip) && !UNKNOWN.equalsIgnoreCase(ip.trim());
    }

    private boolean isValidIp(String ip) {
        return isUsableIp(ip) && InetAddresses.isInetAddress(ip.trim());
    }

    /**
     * 规范化 IP 地址表示。
     * - IPv4-mapped IPv6（如 ::ffff:192.168.1.1）转为纯 IPv4（192.168.1.1）
     * - IPv6 压缩格式统一（如 0:0:0:0:0:0:0:1 → ::1）
     * 确保配置中的 IP 与实际获取的 IP 能正确匹配。
     */
    private String normalizeIp(String ip) {
        if (!isValidIp(ip)) {
            return ip;
        }
        byte[] address = InetAddresses.forString(ip.trim()).getAddress();
        // JVM 的 getHostAddress() 可能返回 ::ffff:192.168.1.1 格式，需转为纯 IPv4
        if (isIpv4MappedIpv6Address(address)) {
            return toDottedDecimal(address);
        }
        return InetAddresses.toAddrString(InetAddresses.forString(ip.trim()));
    }

    /**
     * 判断是否为 IPv4-mapped IPv6 地址。
     * 格式：前 10 字节为 0，第 11-12 字节为 0xFF，后 4 字节为 IPv4 地址。
     * 例如 ::ffff:192.168.1.1 的字节表示为 [0,0,0,0,0,0,0,0,0,0,0xff,0xff,192,168,1,1]
     */
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

    /**
     * 判断 IP 是否在 CIDR 网段内。
     * 例如 10.0.0.5 在 10.0.0.0/8 范围内，192.168.1.1 不在。
     * <p>
     * 算法：比较前 prefixLength 位是否相同。
     * - 先比较完整的字节（fullBytes）
     * - 再比较剩余位（remainingBits），用掩码屏蔽不需要比较的低位
     */
    private boolean isInCidr(byte[] ipBytes, byte[] cidrBytes, int prefixLength) {
        if (ipBytes.length != cidrBytes.length || prefixLength < 0 || prefixLength > ipBytes.length * 8) {
            return false;
        }

        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;
        // 比较完整的字节
        for (int i = 0; i < fullBytes; i++) {
            if (ipBytes[i] != cidrBytes[i]) {
                return false;
            }
        }
        // 没有剩余位，说明正好是整字节边界，已匹配
        if (remainingBits == 0) {
            return true;
        }

        // 比较剩余位：构造掩码（如 remainingBits=3 → mask=11100000=0xE0）
        int mask = (0xff << (8 - remainingBits)) & 0xff;
        return ((ipBytes[fullBytes] & 0xff) & mask) == ((cidrBytes[fullBytes] & 0xff) & mask);
    }
}
