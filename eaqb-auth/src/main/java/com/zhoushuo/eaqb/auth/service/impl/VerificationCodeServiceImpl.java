package com.zhoushuo.eaqb.auth.service.impl;


import cn.hutool.core.util.RandomUtil;
import com.zhoushuo.eaqb.auth.constant.RedisKeyConstants;
import com.zhoushuo.eaqb.auth.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.auth.modle.vo.verificationcode.SendVerificationCodeReqVO;
import com.zhoushuo.eaqb.auth.rpc.UserRpcService;
import com.zhoushuo.eaqb.auth.service.VerificationCodeService;
import com.zhoushuo.eaqb.auth.sms.AliyunSmsHelper;
import com.zhoushuo.eaqb.user.dto.resp.CurrentUserCredentialRspDTO;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class VerificationCodeServiceImpl implements VerificationCodeService {
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

    private Response<?> sendVerificationCode(String phone, String key) {
        checkBlacklist(phone);
        checkPhoneDailyLimit(phone);

        String clientIp = getClientIp();
        if (clientIp != null) {
            checkIpDailyLimit(clientIp);
        }

        boolean isSent = redisTemplate.hasKey(key);
        if (isSent) {
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_SEND_FREQUENTLY);
        }

        String verificationCode = RandomUtil.randomNumbers(6);
        log.info("==> 手机号: {}, 已生成验证码：【{}】", phone, verificationCode);

//        taskExecutor.submit(() -> {
//            String signName = "速通互联验证码";
//            String templateCode = "100001";
//            String templateParam = String.format("{\"code\":\"%s\",\"min\":\"3\"}", verificationCode);
//            aliyunSmsHelper.sendMessage(signName, templateCode, phone, templateParam);
//        });

        redisTemplate.opsForValue().set(key, verificationCode, 3, TimeUnit.MINUTES);
        incrementPhoneDailyCount(phone);
        if (clientIp != null) {
            incrementIpDailyCount(clientIp);
        }

        return Response.success();
    }

    private boolean consumeVerificationCode(String key, String verificationCode) {
        Long result = redisTemplate.execute(
                CONSUME_VERIFICATION_CODE_SCRIPT,
                Collections.singletonList(key),
                verificationCode
        );
        return java.util.Objects.equals(result, 1L);
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
     * 检查手机号每日发送次数限制
     */
    private void checkPhoneDailyLimit(String phone) {
        String dailyCountKey = RedisKeyConstants.buildVerificationCodeDailyCountKey(phone);
        Object countObj = redisTemplate.opsForValue().get(dailyCountKey);
        int count = countObj != null ? Integer.parseInt(countObj.toString()) : 0;

        if (count >= PHONE_DAILY_LIMIT) {
            log.warn("==> 手机号今日发送次数已达上限, phone: {}, count: {}", phone, count);
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_DAILY_LIMIT_EXCEEDED);
        }
    }

    /**
     * 检查 IP 每日发送次数限制
     */
    private void checkIpDailyLimit(String ip) {
        String ipDailyCountKey = RedisKeyConstants.buildVerificationCodeIpDailyCountKey(ip);
        Object countObj = redisTemplate.opsForValue().get(ipDailyCountKey);
        int count = countObj != null ? Integer.parseInt(countObj.toString()) : 0;

        if (count >= IP_DAILY_LIMIT) {
            log.warn("==> IP 今日发送次数已达上限, ip: {}, count: {}", ip, count);
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_IP_DAILY_LIMIT_EXCEEDED);
        }
    }

    /**
     * 增加手机号每日发送次数
     */
    private void incrementPhoneDailyCount(String phone) {
        String dailyCountKey = RedisKeyConstants.buildVerificationCodeDailyCountKey(phone);
        Long count = redisTemplate.opsForValue().increment(dailyCountKey);

        // 如果是第一次发送，设置过期时间为当天结束
        if (count != null && count == 1) {
            redisTemplate.expireAt(dailyCountKey, getEndOfDay());
        }
    }

    /**
     * 增加 IP 每日发送次数
     */
    private void incrementIpDailyCount(String ip) {
        String ipDailyCountKey = RedisKeyConstants.buildVerificationCodeIpDailyCountKey(ip);
        Long count = redisTemplate.opsForValue().increment(ipDailyCountKey);

        // 如果是第一次发送，设置过期时间为当天结束
        if (count != null && count == 1) {
            redisTemplate.expireAt(ipDailyCountKey, getEndOfDay());
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
