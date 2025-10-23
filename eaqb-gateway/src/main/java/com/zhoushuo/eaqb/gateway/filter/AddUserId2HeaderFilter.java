package com.zhoushuo.eaqb.gateway.filter;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j

public class AddUserId2HeaderFilter implements GlobalFilter {

    /**
     * 请求头中，用户 ID 的键
     */
    private static final String HEADER_USER_ID = "userId";

//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
//        log.info("==================> TokenConvertFilter");
//        SaReactorSyncHolder.setContext(exchange);
//        // 添加详细诊断日志
//        log.info("当前线程: {}", Thread.currentThread().getName());
//        log.info("请求路径: {}", exchange.getRequest().getURI().getPath());
//
////        try {
////            // 获取Sa-Token上下文信息
////            Object tokenContext = StpUtil.getTokenInfo();
////            log.info("Sa-Token上下文: {}", tokenContext != null ? tokenContext : "null");
////
////            // 尝试获取Token值
////            String tokenValue = StpUtil.getTokenValue();
////            log.info("Token值: {}", tokenValue != null ? tokenValue : "null");
////
////            // 检查登录状态
////            boolean isLogin = StpUtil.isLogin();
////            log.info("用户登录状态: {}", isLogin);
////        } catch (Exception e) {
////            log.error("获取上下文信息失败: {}", e.getMessage());
////        }
//        // 用户 ID
//        Long userId = null;
//        try {
//            // 获取当前登录用户的 ID
//            userId = StpUtil.getLoginIdAsLong();
//        } catch (Exception e) {
//            //log.error("获取用户ID失败，异常信息: {}", e.getMessage(), e);
//            // 若没有登录，则直接放行
//            return chain.filter(exchange);
//        }
//
//        log.info("## 当前登录的用户 ID: {}", userId);
//
//        Long finalUserId = userId;
//        ServerWebExchange newExchange = exchange.mutate()
//                .request(builder -> builder.header(HEADER_USER_ID, String.valueOf(finalUserId))) // 将用户 ID 设置到请求头中
//                .build();
//
//        // 将请求传递给过滤器链中的下一个过滤器进行处理。没有对请求进行任何修改。
//        return chain.filter(newExchange);
//    }
@Override
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    log.info("==================> TokenConvertFilter");
    SaReactorSyncHolder.setContext(exchange);
    // 添加详细诊断日志
    log.info("当前线程: {}", Thread.currentThread().getName());
    log.info("请求路径: {}", exchange.getRequest().getURI().getPath());

    try {
        // 用户 ID
        Long userId = null;
        try {
            // 获取当前登录用户的 ID
            userId = StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            //log.error("获取用户ID失败，异常信息: {}", e.getMessage(), e);
            // 若没有登录，则直接放行
            return chain.filter(exchange);
        }

        log.info("## 当前登录的用户 ID: {}", userId);

        Long finalUserId = userId;
        ServerWebExchange newExchange = exchange.mutate()
                .request(builder -> builder.header(HEADER_USER_ID, String.valueOf(finalUserId))) // 将用户 ID 设置到请求头中
                .build();

        // 将请求传递给过滤器链中的下一个过滤器进行处理。没有对请求进行任何修改。
        return chain.filter(newExchange);
    } finally {
        // 清理Sa-Token上下文，防止内存泄漏
        SaReactorSyncHolder.clearContext();
    }
}

}