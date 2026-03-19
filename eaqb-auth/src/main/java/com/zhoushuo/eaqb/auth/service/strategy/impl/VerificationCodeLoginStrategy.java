package com.zhoushuo.eaqb.auth.service.strategy.impl;

import com.zhoushuo.eaqb.auth.constant.RedisKeyConstants;
import com.zhoushuo.eaqb.auth.enums.LoginTypeEnum;
import com.zhoushuo.eaqb.auth.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.auth.modle.vo.user.UserLoginReqVO;
import com.zhoushuo.eaqb.auth.rpc.UserRpcService;
import com.zhoushuo.eaqb.auth.service.strategy.LoginStrategy;
import com.zhoushuo.framework.commono.exception.BizException;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Objects;

/**
 * 验证码登录策略。
 */
@Component
public class VerificationCodeLoginStrategy implements LoginStrategy {
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
    private UserRpcService userRpcService;

    @Override
    public LoginTypeEnum getLoginType() {
        return LoginTypeEnum.VERIFICATION_CODE;
    }

    @Override
    public Long login(UserLoginReqVO userLoginReqVO) {
        String phone = userLoginReqVO.getPhone();
        String verificationCode = userLoginReqVO.getCode();

        if (StringUtils.isBlank(verificationCode)) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), "验证码不能为空");
        }

        String key = RedisKeyConstants.buildVerificationCodeKey(phone);
        if (!consumeVerificationCode(key, verificationCode)) {
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
        }

        // 验证码登录采用“注册/登录合一”语义：
        // 若手机号已存在，则直接返回已有用户 ID；若手机号未注册，则自动完成注册后返回新用户 ID。
        return userRpcService.registerUser(phone);
    }

    private boolean consumeVerificationCode(String key, String verificationCode) {
        Long result = redisTemplate.execute(
                CONSUME_VERIFICATION_CODE_SCRIPT,
                Collections.singletonList(key),
                verificationCode
        );
        return Objects.equals(result, 1L);
    }
}
