package com.zhoushuo.eaqb.gateway.config;

import com.google.common.net.InetAddresses;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
     * 启动时校验配置合法性，避免运行时才发现配置错误。
     * 校验不通过会抛出 IllegalArgumentException，阻止应用启动。
     */
    @PostConstruct
    public void validate() {
        for (String trustedProxy : trustedProxies) {
            validateTrustedProxy(trustedProxy);
        }
    }

    private void validateTrustedProxy(String trustedProxy) {
        if (StringUtils.isBlank(trustedProxy)) {
            throw new IllegalArgumentException("gateway.client-ip.trusted-proxies contains blank value");
        }

        String normalizedTrustedProxy = trustedProxy.trim();
        if (!normalizedTrustedProxy.contains("/")) {
            if (!InetAddresses.isInetAddress(normalizedTrustedProxy)) {
                throw new IllegalArgumentException("Invalid trusted proxy IP: " + trustedProxy);
            }
            return;
        }

        String[] cidrParts = normalizedTrustedProxy.split("/", 2);
        if (cidrParts.length != 2
                || !InetAddresses.isInetAddress(cidrParts[0])
                || !StringUtils.isNumeric(cidrParts[1])) {
            throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + trustedProxy);
        }

        int prefixLength = Integer.parseInt(cidrParts[1]);
        int addressBits = InetAddresses.forString(cidrParts[0]).getAddress().length * 8;
        if (prefixLength < 0 || prefixLength > addressBits) {
            throw new IllegalArgumentException("Invalid trusted proxy CIDR prefix: " + trustedProxy);
        }
    }
}
