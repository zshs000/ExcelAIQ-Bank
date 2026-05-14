package com.zhoushuo.eaqb.gateway.config;

import com.google.common.net.InetAddresses;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 网关客户端 IP 解析配置。
 * <p>
 * 配置项：gateway.client-ip.trusted-proxies
 * 用于指定可信的直连代理 IP 或 CIDR 网段。
 * <p>
 * 典型配置：
 * - 开发环境：127.0.0.1, ::1（本机代理）
 * - 生产环境：Nginx / SLB / CDN / Ingress 的出口 IP 或网段
 * <p>
 * 解析逻辑见 {@link com.zhoushuo.eaqb.gateway.filter.ClientIpHeaderFilter}
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway.client-ip")
public class ClientIpProperties {

    /**
     * 可信的直连代理 IP 或 CIDR 网段列表。
     * <p>
     * 支持两种格式：
     * - 单个 IP：如 127.0.0.1、::1
     * - CIDR 网段：如 10.0.0.0/8、172.16.0.0/12
     */
    private List<String> trustedProxies = new ArrayList<>();

    /**
     * 预解析后的可信代理匹配器列表，在 {@link #validate()} 时初始化。
     * 运行时直接使用字节比较，避免每次请求重复解析字符串。
     */
    private List<TrustedProxyMatcher> trustedProxyMatchers = Collections.emptyList();

    /**
     * 启动时校验配置合法性，避免运行时才发现配置错误。
     * 校验不通过会抛出 IllegalArgumentException，阻止应用启动。
     */
    @PostConstruct
    public void validate() {
        List<TrustedProxyMatcher> parsedMatchers = new ArrayList<>();
        for (String trustedProxy : trustedProxies) {
            parsedMatchers.add(TrustedProxyMatcher.parse(trustedProxy));
        }
        trustedProxyMatchers = parsedMatchers;
    }

    /**
     * 判断 IP 是否在可信代理列表中。
     *
     * @param ip IP 地址字符串（支持 IPv4、IPv6、IPv4-mapped IPv6）
     * @return true 如果 IP 匹配任一可信代理或 CIDR 网段
     */
    public boolean isTrustedProxy(String ip) {
        IpAddress ipAddress = IpAddress.parse(ip);
        if (ipAddress == null) {
            return false;
        }
        return trustedProxyMatchers.stream().anyMatch(matcher -> matcher.matches(ipAddress));
    }

    /**
     * 规范化 IP 地址表示。
     * - IPv4-mapped IPv6（如 ::ffff:192.168.1.1）转为纯 IPv4（192.168.1.1）
     * - IPv6 压缩格式统一（如 0:0:0:0:0:0:0:1 → ::1）
     *
     * @param ip 原始 IP 字符串
     * @return 规范化后的 IP 字符串，无效输入返回 null
     */
    public static String normalizeIp(String ip) {
        IpAddress ipAddress = IpAddress.parse(ip);
        return ipAddress == null ? null : ipAddress.normalized();
    }

    /**
     * 可信代理匹配器，预解析 IP/CIDR 为字节数组，运行时直接做字节比较。
     * <p>
     * 单个 IP 的 prefixLength 为地址位数（IPv4=32，IPv6=128），等价于精确匹配。
     * CIDR 网段的 prefixLength 为网络前缀长度（如 /8 表示前 8 位）。
     */
    private record TrustedProxyMatcher(byte[] address, int prefixLength) {

        private static TrustedProxyMatcher parse(String trustedProxy) {
            if (StringUtils.isBlank(trustedProxy)) {
                throw new IllegalArgumentException("gateway.client-ip.trusted-proxies contains blank value");
            }

            String normalizedTrustedProxy = trustedProxy.trim();
            if (!normalizedTrustedProxy.contains("/")) {
                IpAddress ipAddress = IpAddress.parse(normalizedTrustedProxy);
                if (ipAddress == null) {
                    throw new IllegalArgumentException("Invalid trusted proxy IP: " + trustedProxy);
                }
                return new TrustedProxyMatcher(ipAddress.address(), ipAddress.address().length * 8);
            }

            String[] cidrParts = normalizedTrustedProxy.split("/", 2);
            if (cidrParts.length != 2 || !StringUtils.isNumeric(cidrParts[1])) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + trustedProxy);
            }

            IpAddress ipAddress = IpAddress.parse(cidrParts[0]);
            if (ipAddress == null) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + trustedProxy);
            }

            int prefixLength = Integer.parseInt(cidrParts[1]);
            int addressBits = ipAddress.address().length * 8;
            if (prefixLength < 0 || prefixLength > addressBits) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR prefix: " + trustedProxy);
            }
            return new TrustedProxyMatcher(ipAddress.address(), prefixLength);
        }

        private boolean matches(IpAddress ipAddress) {
            return isInCidr(ipAddress.address(), address, prefixLength);
        }
    }

    /**
     * 解析后的 IP 地址，封装字节表示和规范化字符串，避免重复解析。
     * <p>
     * - address: 原始字节（IPv4 为 4 字节，IPv6 为 16 字节）
     * - normalized: 规范化后的字符串（IPv4-mapped IPv6 会转为纯 IPv4）
     */
    private record IpAddress(byte[] address, String normalized) {

        private static IpAddress parse(String ip) {
            if (StringUtils.isBlank(ip)) {
                return null;
            }

            InetAddress inetAddress;
            try {
                inetAddress = InetAddresses.forString(ip.trim());
            } catch (IllegalArgumentException e) {
                return null;
            }

            byte[] address = inetAddress.getAddress();
            if (isIpv4MappedIpv6Address(address)) {
                return new IpAddress(toIpv4Bytes(address), toDottedDecimal(address));
            }
            return new IpAddress(address, InetAddresses.toAddrString(inetAddress));
        }
    }

    /**
     * 判断 IP 是否在 CIDR 网段内。
     * 例如 10.0.0.5 在 10.0.0.0/8 范围内，192.168.1.1 不在。
     * <p>
     * 算法：比较前 prefixLength 位是否相同。
     * - 先比较完整的字节（fullBytes）
     * - 再比较剩余位（remainingBits），用掩码屏蔽不需要比较的低位
     */
    private static boolean isInCidr(byte[] ipBytes, byte[] cidrBytes, int prefixLength) {
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

    /**
     * 判断是否为 IPv4-mapped IPv6 地址。
     * 格式：前 10 字节为 0，第 11-12 字节为 0xFF，后 4 字节为 IPv4 地址。
     * 例如 ::ffff:192.168.1.1 的字节表示为 [0,0,0,0,0,0,0,0,0,0,0xff,0xff,192,168,1,1]
     */
    private static boolean isIpv4MappedIpv6Address(byte[] address) {
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

    /**
     * 从 IPv4-mapped IPv6 地址中提取 IPv4 字节（后 4 字节）。
     */
    private static byte[] toIpv4Bytes(byte[] ipv4MappedAddress) {
        byte[] ipv4Bytes = new byte[4];
        System.arraycopy(ipv4MappedAddress, 12, ipv4Bytes, 0, 4);
        return ipv4Bytes;
    }

    /**
     * 将 IPv4-mapped IPv6 地址的后 4 字节转为点分十进制格式。
     */
    private static String toDottedDecimal(byte[] ipv4MappedAddress) {
        return (ipv4MappedAddress[12] & 0xff) + "."
                + (ipv4MappedAddress[13] & 0xff) + "."
                + (ipv4MappedAddress[14] & 0xff) + "."
                + (ipv4MappedAddress[15] & 0xff);
    }

}
