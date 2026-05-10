package com.zhoushuo.eaqb.auth.service.strategy.impl;

import com.zhoushuo.eaqb.auth.enums.LoginTypeEnum;
import com.zhoushuo.eaqb.auth.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.auth.model.vo.user.UserLoginReqVO;
import com.zhoushuo.eaqb.auth.rpc.UserRpcService;
import com.zhoushuo.eaqb.user.dto.resp.FindUserByPhoneRspDTO;
import com.zhoushuo.framework.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordLoginStrategyTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserRpcService userRpcService;

    @InjectMocks
    private PasswordLoginStrategy passwordLoginStrategy;

    @Test
    void login_blankPassword_shouldThrowParamNotValid() {
        UserLoginReqVO request = UserLoginReqVO.builder()
                .phone("13800138002")
                .password(" ")
                .type(LoginTypeEnum.PASSWORD.getValue())
                .build();

        BizException ex = assertThrows(BizException.class, () -> passwordLoginStrategy.login(request));

        assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), ex.getErrorCode());
        assertEquals("密码不能为空", ex.getErrorMessage());
    }

    @Test
    void login_userNotFound_shouldThrowUserNotFound() {
        String phone = "13800138002";
        UserLoginReqVO request = UserLoginReqVO.builder()
                .phone(phone)
                .password("123456")
                .type(LoginTypeEnum.PASSWORD.getValue())
                .build();

        when(userRpcService.findUserByPhone(phone)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> passwordLoginStrategy.login(request));

        assertEquals(ResponseCodeEnum.USER_NOT_FOUND.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void login_passwordNotInitialized_shouldThrowPasswordNotInitialized() {
        String phone = "13800138009";
        UserLoginReqVO request = UserLoginReqVO.builder()
                .phone(phone)
                .password("123456")
                .type(LoginTypeEnum.PASSWORD.getValue())
                .build();
        FindUserByPhoneRspDTO response = new FindUserByPhoneRspDTO();
        response.setId(1L);
        response.setPasswordHash("");

        when(userRpcService.findUserByPhone(phone)).thenReturn(response);

        BizException ex = assertThrows(BizException.class, () -> passwordLoginStrategy.login(request));

        assertEquals(ResponseCodeEnum.PASSWORD_NOT_INITIALIZED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void login_passwordMismatch_shouldThrowPhoneOrPasswordError() {
        String phone = "13800138003";
        UserLoginReqVO request = UserLoginReqVO.builder()
                .phone(phone)
                .password("wrong-password")
                .type(LoginTypeEnum.PASSWORD.getValue())
                .build();
        FindUserByPhoneRspDTO response = new FindUserByPhoneRspDTO();
        response.setId(1L);
        response.setPasswordHash("encoded-password");

        when(userRpcService.findUserByPhone(phone)).thenReturn(response);
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> passwordLoginStrategy.login(request));

        assertEquals(ResponseCodeEnum.PHONE_OR_PASSWORD_ERROR.getErrorCode(), ex.getErrorCode());
    }
}
