package com.zhoushuo.eaqb.gateway.config;

import com.google.common.net.InetAddresses;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "gateway.client-ip")
public class ClientIpProperties {

    /**
     * Trusted direct upstream proxy IPs or CIDR ranges.
     */
    private List<String> trustedProxies = new ArrayList<>();

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
