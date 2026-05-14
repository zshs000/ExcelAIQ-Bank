package com.zhoushuo.eaqb.gateway.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientIpPropertiesTest {

    @Test
    void shouldAcceptIpAndCidrTrustedProxies() {
        ClientIpProperties properties = new ClientIpProperties();
        properties.getTrustedProxies().add("127.0.0.1");
        properties.getTrustedProxies().add("10.0.0.0/8");
        properties.getTrustedProxies().add("::1");

        assertDoesNotThrow(properties::validate);
    }

    @Test
    void shouldRejectInvalidTrustedProxy() {
        ClientIpProperties properties = new ClientIpProperties();
        properties.getTrustedProxies().add("10.0.0.0/33");

        assertThrows(IllegalArgumentException.class, properties::validate);
    }
}
