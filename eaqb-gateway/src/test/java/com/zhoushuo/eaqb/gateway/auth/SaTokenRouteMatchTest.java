package com.zhoushuo.eaqb.gateway.auth;

import cn.dev33.satoken.util.SaFoxUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 SaRouter 路径匹配逻辑
 * 核心问题：SaReactorFilter 是 WebFilter，在 StripPrefix GatewayFilter 之前执行
 * 因此 SaRouter 看到的是原始请求路径（带路由前缀）
 */
class SaTokenRouteMatchTest {

    /**
     * 模拟当前鉴权配置的匹配逻辑
     */
    private boolean matchesWithCurrentConfig(String requestPath) {
        return SaFoxUtil.vagueMatch(SaTokenConfigure.QUESTION_BANK_ADMIN_ROUTE_PATTERN, requestPath);
    }

    /**
     * 模拟旧配置（错误）的匹配逻辑
     */
    private boolean matchesWithOldConfig(String requestPath) {
        return SaFoxUtil.vagueMatch("/question/admin/**", requestPath);
    }

    @Test
    @DisplayName("新配置：/question-bank/question/admin/** 应匹配外部请求路径")
    void newConfig_shouldMatchExternalPath() {
        // 外部请求路径（带网关路由前缀）
        String externalPath = "/question-bank/question/admin/outbox/failed";

        assertTrue(matchesWithCurrentConfig(externalPath),
                "新配置应匹配带前缀的外部路径: " + externalPath);
    }

    @Test
    @DisplayName("新配置：应匹配管理员重试接口")
    void newConfig_shouldMatchAdminRetryPath() {
        String externalPath = "/question-bank/question/admin/outbox/123/retry";

        assertTrue(matchesWithCurrentConfig(externalPath),
                "新配置应匹配管理员重试接口: " + externalPath);
    }

    @Test
    @DisplayName("旧配置：/question/admin/** 不应匹配外部请求路径")
    void oldConfig_shouldNotMatchExternalPath() {
        // 外部请求路径（带网关路由前缀）
        String externalPath = "/question-bank/question/admin/outbox/failed";

        assertFalse(matchesWithOldConfig(externalPath),
                "旧配置不应匹配带前缀的外部路径: " + externalPath);
    }

    @Test
    @DisplayName("旧配置：只匹配不带前缀的路径（下游内部路径）")
    void oldConfig_shouldMatchInternalPath() {
        // 下游服务内部路径（StripPrefix 之后）
        String internalPath = "/question/admin/outbox/failed";

        assertTrue(matchesWithOldConfig(internalPath),
                "旧配置只匹配 StripPrefix 后的内部路径: " + internalPath);
    }

    @Test
    @DisplayName("新配置：不应匹配非管理员接口")
    void newConfig_shouldNotMatchNonAdminPath() {
        String normalPath = "/question-bank/question/list";

        assertFalse(matchesWithCurrentConfig(normalPath),
                "不应匹配非管理员接口: " + normalPath);
    }

    @Test
    @DisplayName("验证 user 管理员接口路径匹配")
    void shouldMatchUserAdminPath() {
        String externalPath = "/user/user/admin/list";

        assertTrue(SaFoxUtil.vagueMatch(SaTokenConfigure.USER_ADMIN_ROUTE_PATTERN, externalPath),
                "应匹配 user 管理员接口的外部路径: " + externalPath);
    }
}
