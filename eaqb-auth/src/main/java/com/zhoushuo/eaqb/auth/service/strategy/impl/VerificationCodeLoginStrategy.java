package com.zhoushuo.eaqb.auth.service.strategy.impl;

import com.zhoushuo.eaqb.auth.enums.LoginTypeEnum;
import com.zhoushuo.eaqb.auth.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.auth.model.vo.user.UserLoginReqVO;
import com.zhoushuo.eaqb.auth.rpc.UserRpcService;
import com.zhoushuo.eaqb.auth.service.VerificationCodeService;
import com.zhoushuo.eaqb.auth.service.strategy.LoginStrategy;
import com.zhoushuo.framework.common.exception.BizException;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 验证码登录策略。
 */
@Component
public class VerificationCodeLoginStrategy implements LoginStrategy {
    @Resource
    private VerificationCodeService verificationCodeService;

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

        if (!verificationCodeService.consumeLoginVerificationCode(phone, verificationCode)) {
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
        }

        // 验证码登录采用“注册/登录合一”语义：
        // 若手机号已存在，则直接返回已有用户 ID；若手机号未注册，则自动完成注册后返回新用户 ID。
        return userRpcService.registerUser(phone);
    }
}
