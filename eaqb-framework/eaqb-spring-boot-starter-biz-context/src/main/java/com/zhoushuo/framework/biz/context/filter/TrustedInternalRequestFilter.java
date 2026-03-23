package com.zhoushuo.framework.biz.context.filter;

import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.constant.GlobalConstants;
import com.zhoushuo.framework.commono.response.Response;
import com.zhoushuo.framework.commono.util.InternalRequestAuthUtil;
import com.zhoushuo.framework.commono.util.JsonUtils;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;


@Slf4j
public class TrustedInternalRequestFilter extends OncePerRequestFilter {
    private static final Set<String> TRUSTED_PATH_PREFIXES = Set.of("/actuator", "/error");
    private static final String UNAUTHORIZED_ERROR_CODE = "COMMON-401";
    private static final String UNAUTHORIZED_MESSAGE = "非法内部调用，请通过网关或受信任的内部服务访问";

    private final String internalAuthSecret;
    private final long maxSkewSeconds;

    public TrustedInternalRequestFilter(String internalAuthSecret, long maxSkewSeconds) {
        this.internalAuthSecret = internalAuthSecret;
        this.maxSkewSeconds = maxSkewSeconds;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (StringUtils.isBlank(requestUri)) {
            return false;
        }
        return TRUSTED_PATH_PREFIXES.stream().anyMatch(requestUri::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String userId = request.getHeader(GlobalConstants.USER_ID);
        String callerService = request.getHeader(GlobalConstants.INTERNAL_CALLER_SERVICE);
        String timestampHeader = request.getHeader(GlobalConstants.INTERNAL_CALL_TIMESTAMP);
        String signature = request.getHeader(GlobalConstants.INTERNAL_CALL_SIGNATURE);

        if (!isTrustedInternalCall(request, userId, callerService, timestampHeader, signature)) {
            writeUnauthorizedResponse(response);
            return;
        }

        if (StringUtils.isBlank(userId)) {
            chain.doFilter(request, response);
            return;
        }

        LoginUserContextHolder.setUserId(userId);

        try {
            chain.doFilter(request, response);
        } finally {
            LoginUserContextHolder.remove();
        }
    }

    private boolean isTrustedInternalCall(HttpServletRequest request, String userId, String callerService,
                                          String timestampHeader, String signature) {
        if (StringUtils.isBlank(callerService) || StringUtils.isBlank(timestampHeader) || StringUtils.isBlank(signature)) {
            log.warn("拒绝未签名的内部调用, path={}", request.getRequestURI());
            return false;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException e) {
            log.warn("拒绝非法时间戳的内部调用, path={}, timestamp={}", request.getRequestURI(), timestampHeader);
            return false;
        }

        if (!InternalRequestAuthUtil.isTimestampFresh(timestamp, maxSkewSeconds)) {
            log.warn("拒绝过期的内部调用, path={}, callerService={}", request.getRequestURI(), callerService);
            return false;
        }

        boolean verified = InternalRequestAuthUtil.verify(
                internalAuthSecret,
                callerService,
                userId,
                timestamp,
                request.getMethod(),
                signature
        );
        if (!verified) {
            log.warn("拒绝验签失败的内部调用, path={}, callerService={}", request.getRequestURI(), callerService);
        }
        return verified;
    }

    private void writeUnauthorizedResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JsonUtils.toJsonString(Response.fail(UNAUTHORIZED_ERROR_CODE, UNAUTHORIZED_MESSAGE)));
    }
}
