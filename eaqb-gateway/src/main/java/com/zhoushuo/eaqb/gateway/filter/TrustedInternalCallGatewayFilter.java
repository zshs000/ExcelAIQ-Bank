package com.zhoushuo.eaqb.gateway.filter;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import com.zhoushuo.framework.commono.constant.GlobalConstants;
import com.zhoushuo.framework.commono.util.InternalRequestAuthUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Order(0)
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
                        headers.remove(GlobalConstants.USER_ID);
                        headers.remove(GlobalConstants.INTERNAL_CALLER_SERVICE);
                        headers.remove(GlobalConstants.INTERNAL_CALL_TIMESTAMP);
                        headers.remove(GlobalConstants.INTERNAL_CALL_SIGNATURE);
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

    // 抽出一个极小的可覆写钩子，仅用于测试替换当前登录用户获取逻辑，生产行为保持不变。
    protected Long resolveLoginUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception ignored) {
            // 未登录请求也需要透传受信签名，避免下游把它当作非法直连请求。
            return null;
        }
    }

}
