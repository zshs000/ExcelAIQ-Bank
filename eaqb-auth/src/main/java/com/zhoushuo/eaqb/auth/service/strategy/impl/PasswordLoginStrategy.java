package com.zhoushuo.eaqb.auth.service.strategy.impl;

import com.zhoushuo.eaqb.auth.enums.LoginTypeEnum;
import com.zhoushuo.eaqb.auth.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.auth.modle.vo.user.UserLoginReqVO;
import com.zhoushuo.eaqb.auth.rpc.UserRpcService;
import com.zhoushuo.eaqb.auth.service.strategy.LoginStrategy;
import com.zhoushuo.eaqb.user.dto.resp.FindUserByPhoneRspDTO;
import com.zhoushuo.framework.commono.exception.BizException;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 密码登录策略。
 */
@Component
public class PasswordLoginStrategy implements LoginStrategy {

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private UserRpcService userRpcService;

    @Override
    public LoginTypeEnum getLoginType() {
        return LoginTypeEnum.PASSWORD;
    }

    @Override
    public Long login(UserLoginReqVO userLoginReqVO) {
        String phone = userLoginReqVO.getPhone();
        String password = userLoginReqVO.getPassword();

        if (StringUtils.isBlank(password)) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), "密码不能为空");
        }

        FindUserByPhoneRspDTO findUserByPhoneRspDTO = userRpcService.findUserByPhone(phone);
        if (Objects.isNull(findUserByPhoneRspDTO)) {
            throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
        }

        String encodePassword = findUserByPhoneRspDTO.getPasswordHash();
        if (StringUtils.isBlank(encodePassword)) {
            throw new BizException(ResponseCodeEnum.PASSWORD_NOT_INITIALIZED);
        }
        boolean isPasswordCorrect = passwordEncoder.matches(password, encodePassword);
        if (!isPasswordCorrect) {
            throw new BizException(ResponseCodeEnum.PHONE_OR_PASSWORD_ERROR);
        }
        return findUserByPhoneRspDTO.getId();
    }
}
