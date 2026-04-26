package com.zhoushuo.eaqb.gateway.filter;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import com.zhoushuo.framework.commono.constant.GlobalConstants;
import com.zhoushuo.framework.commono.util.InternalRequestAuthUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 受信内部调用网关过滤器
 * 功能：
 * 1. 从 Sa-Token 获取已解析的 userId（登录态解析收敛于网关）
 * 2. 生成内部调用签名并添加到请求头
 * 3. 移除外部请求可能伪造的受信头
 * 4. 将 userId 透传给下游服务，避免下游重复解析 Token
 * 
 * Order 设置为 10，确保在 Sa-Token 认证过滤器之后执行
 */
@Component
@Order(10)
@Slf4j
public class TrustedInternalCallGatewayFilter implements GlobalFilter {
    @Value("${spring.application.name:eaqb-gateway}")
    private String callerService;

    @Value("${internal.auth.secret:change-me-in-prod}")
    private String internalAuthSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 基于 Reactor Context 解决 Gateway 与 Sa-Token 在响应式场景下的上下文兼容问题。
        SaReactorSyncHolder.setContext(exchange);
        try {
            Long userId = resolveLoginUserId();
            log.debug("透传 userId: {} 到下游服务", userId);

            long timestamp = System.currentTimeMillis();
            String method = exchange.getRequest().getMethod() == null
                    ? null
                    : exchange.getRequest().getMethod().name();
            String userIdHeader = userId == null ? null : String.valueOf(userId);
            String signature = InternalRequestAuthUtil.sign(
                    internalAuthSecret,
                    callerService,
                    userIdHeader,
                    timestamp,
                    method
            );

            ServerWebExchange newExchange = exchange.mutate()
                    .request(builder -> builder.headers(headers -> {
                        // 先移除外部请求可能伪造的受信头，确保安全性
                        headers.remove(GlobalConstants.USER_ID);
                        headers.remove(GlobalConstants.INTERNAL_CALLER_SERVICE);
                        headers.remove(GlobalConstants.INTERNAL_CALL_TIMESTAMP);
                        headers.remove(GlobalConstants.INTERNAL_CALL_SIGNATURE);
                        
                        // 添加网关生成的受信头
                        headers.set(GlobalConstants.INTERNAL_CALLER_SERVICE, callerService);
                        headers.set(GlobalConstants.INTERNAL_CALL_TIMESTAMP, String.valueOf(timestamp));
                        headers.set(GlobalConstants.INTERNAL_CALL_SIGNATURE, signature);
                        if (userIdHeader != null) {
                            headers.set(GlobalConstants.USER_ID, userIdHeader);
                        }
                    }))
                    .build();
            return chain.filter(newExchange);
        } finally {
            SaReactorSyncHolder.clearContext();
        }
    }

    /**
     * 从 Sa-Token 获取当前登录用户 ID
     * 登录态解析已收敛于网关，下游服务只需信任并使用透传的 userId
     */
    protected Long resolveLoginUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception ignored) {
            // 未登录请求也需要透传受信签名，避免下游把它当作非法直连请求。
            log.debug("当前请求未登录，透传 null userId");
            return null;
        }
    }

}
