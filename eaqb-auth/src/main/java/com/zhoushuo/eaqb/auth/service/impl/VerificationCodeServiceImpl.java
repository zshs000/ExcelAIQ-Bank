package com.zhoushuo.eaqb.auth.service.impl;


import cn.hutool.core.util.RandomUtil;
import com.zhoushuo.eaqb.auth.constant.RedisKeyConstants;
import com.zhoushuo.eaqb.auth.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.auth.model.vo.verificationcode.SendVerificationCodeReqVO;
import com.zhoushuo.eaqb.auth.rpc.UserRpcService;
import com.zhoushuo.eaqb.auth.service.VerificationCodeService;
import com.zhoushuo.eaqb.auth.sms.AliyunSmsHelper;
import com.zhoushuo.eaqb.user.dto.resp.CurrentUserCredentialRspDTO;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.common.exception.BizException;
import com.zhoushuo.framework.common.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class VerificationCodeServiceImpl implements VerificationCodeService {
    private static final DefaultRedisScript<Long> SEND_VERIFICATION_CODE_SCRIPT = new DefaultRedisScript<>();

    static {
        SEND_VERIFICATION_CODE_SCRIPT.setLocation(new ClassPathResource("lua/send-verification-code.lua"));
        SEND_VERIFICATION_CODE_SCRIPT.setResultType(Long.class);
    }

    private static final DefaultRedisScript<Long> CONSUME_VERIFICATION_CODE_SCRIPT =
            new DefaultRedisScript<>(
                    "local current = redis.call('GET', KEYS[1]); " +
                            "if current == ARGV[1] then " +
                            "  redis.call('DEL', KEYS[1]); " +
                            "  return 1; " +
                            "end; " +
                            "return 0;",
                    Long.class
            );

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private ThreadPoolTaskExecutor taskExecutor;
    @Resource
    private AliyunSmsHelper aliyunSmsHelper;
    @Resource
    private UserRpcService userRpcService;

    /**
     * 每日每个手机号最多发送验证码次数
     */
    private static final int PHONE_DAILY_LIMIT = 10;

    /**
     * 每日每个 IP 最多发送验证码次数
     */
    private static final int IP_DAILY_LIMIT = 50;

    /**
     * 发送短信验证码
     *
     * @param sendVerificationCodeReqVO
     * @return
     */
    @Override
    public Response<?> send(SendVerificationCodeReqVO sendVerificationCodeReqVO) {
        return sendVerificationCode(
                sendVerificationCodeReqVO.getPhone(),
                RedisKeyConstants.buildLoginVerificationCodeKey(sendVerificationCodeReqVO.getPhone())
        );
    }

    @Override
    public Response<?> sendPasswordUpdateCode() {
        requireCurrentLoginUser();
        CurrentUserCredentialRspDTO currentUserPhone = userRpcService.getCurrentUserCredential();
        return sendVerificationCode(
                currentUserPhone.getPhone(),
                RedisKeyConstants.buildPasswordUpdateVerificationCodeKey(currentUserPhone.getPhone())
        );
    }

    @Override
    public boolean consumeLoginVerificationCode(String phone, String verificationCode) {
        return consumeVerificationCode(RedisKeyConstants.buildLoginVerificationCodeKey(phone), verificationCode);
    }

    @Override
    public boolean consumePasswordUpdateVerificationCode(String phone, String verificationCode) {
        return consumeVerificationCode(RedisKeyConstants.buildPasswordUpdateVerificationCodeKey(phone), verificationCode);
    }

    /**
     * 发送验证码。
     * 日额度检查、验证码创建和计数在 Redis 内一次执行，业务拒绝不扣额度。
     * 计数表示取得发送资格；验证码仍兼作冷却 key，消费后可以再次申请。
     */
    private Response<?> sendVerificationCode(String phone, String key) {
        checkBlacklist(phone);
        String clientIp = getClientIp();
        String verificationCode = RandomUtil.randomNumbers(6);
        acquireSendQuota(phone, clientIp, key, verificationCode);

        log.info("==> 手机号: {}, 已生成验证码：【{}】", phone, verificationCode);

//        taskExecutor.submit(() -> {
//            String signName = "速通互联验证码";
//            String templateCode = "100001";
//            String templateParam = String.format("{\"code\":\"%s\",\"min\":\"3\"}", verificationCode);
//            aliyunSmsHelper.sendMessage(signName, templateCode, phone, templateParam);
//        });

        return Response.success();
    }

    private void acquireSendQuota(String phone, String clientIp, String codeKey, String verificationCode) {
        List<String> keys = new ArrayList<>();
        keys.add(codeKey);
        keys.add(RedisKeyConstants.buildVerificationCodeDailyCountKey(phone));
        if (clientIp != null) {
            keys.add(RedisKeyConstants.buildVerificationCodeIpDailyCountKey(clientIp));
        }

        // 沿用模板的 JSON 序列化：验证码原样存入 ARGV[1]，与消费脚本保持一致。
        // 数值参数传 Integer / Long，避免序列化成带引号的字符串。
        Long result = redisTemplate.execute(SEND_VERIFICATION_CODE_SCRIPT, keys,
                verificationCode, PHONE_DAILY_LIMIT, IP_DAILY_LIMIT,
                TimeUnit.MINUTES.toMillis(3), getEndOfDay().getTime());

        if (java.util.Objects.equals(result, 1L)) {
            return;
        }
        if (java.util.Objects.equals(result, -1L)) {
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_DAILY_LIMIT_EXCEEDED);
        }
        if (java.util.Objects.equals(result, -2L)) {
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_IP_DAILY_LIMIT_EXCEEDED);
        }
        if (java.util.Objects.equals(result, -3L)) {
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_SEND_FREQUENTLY);
        }
        log.error("==> 验证码发送资格脚本返回异常结果: {}", result);
        throw new BizException(ResponseCodeEnum.SYSTEM_ERROR);
    }

    private boolean consumeVerificationCode(String key, String verificationCode) {
        Long result = redisTemplate.execute(
                CONSUME_VERIFICATION_CODE_SCRIPT,
                Collections.singletonList(key),
                verificationCode
        );
        return java.util.Objects.equals(result, 1L);
    }

    private void requireCurrentLoginUser() {
        if (LoginUserContextHolder.getUserId() == null) {
            throw new BizException(ResponseCodeEnum.UNAUTHORIZED);
        }
    }

    /**
     * 检查手机号是否在黑名单中
     */
    private void checkBlacklist(String phone) {
        String blacklistKey = RedisKeyConstants.buildVerificationCodeBlacklistKey(phone);
        Boolean isBlacklisted = redisTemplate.hasKey(blacklistKey);
        if (Boolean.TRUE.equals(isBlacklisted)) {
            log.warn("==> 手机号在黑名单中，拒绝发送验证码, phone: {}", phone);
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_PHONE_IN_BLACKLIST);
        }
    }

    /**
     * 获取客户端 IP（从请求上下文中获取）
     */
    private String getClientIp() {
        try {
            // 从 Spring 上下文获取当前请求
            org.springframework.web.context.request.RequestAttributes requestAttributes =
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (requestAttributes != null) {
                jakarta.servlet.http.HttpServletRequest request =
                        ((org.springframework.web.context.request.ServletRequestAttributes) requestAttributes).getRequest();

                // 优先从代理头获取真实 IP
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }

                // X-Forwarded-For 可能包含多个 IP，取第一个
                if (ip != null && ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }

                return ip;
            }
        } catch (Exception e) {
            log.warn("==> 获取客户端 IP 失败", e);
        }
        return null;
    }

    /**
     * 获取当天结束时间
     */
    private java.util.Date getEndOfDay() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23);
        calendar.set(java.util.Calendar.MINUTE, 59);
        calendar.set(java.util.Calendar.SECOND, 59);
        calendar.set(java.util.Calendar.MILLISECOND, 999);
        return calendar.getTime();
    }
}
