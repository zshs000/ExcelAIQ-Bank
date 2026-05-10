package com.zhoushuo.eaqb.auth.service.strategy.impl;

import com.zhoushuo.eaqb.auth.enums.LoginTypeEnum;
import com.zhoushuo.eaqb.auth.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.auth.model.vo.user.UserLoginReqVO;
import com.zhoushuo.eaqb.auth.rpc.UserRpcService;
import com.zhoushuo.eaqb.auth.service.VerificationCodeService;
import com.zhoushuo.framework.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationCodeLoginStrategyTest {

    @Mock
    private VerificationCodeService verificationCodeService;

    @Mock
    private UserRpcService userRpcService;

    @InjectMocks
    private VerificationCodeLoginStrategy verificationCodeLoginStrategy;

    @Test
    void login_blankVerificationCode_shouldThrowParamNotValid() {
        UserLoginReqVO request = UserLoginReqVO.builder()
                .phone("13800138000")
                .code(" ")
                .type(LoginTypeEnum.VERIFICATION_CODE.getValue())
                .build();

        BizException ex = assertThrows(BizException.class, () -> verificationCodeLoginStrategy.login(request));

        assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), ex.getErrorCode());
        assertEquals("验证码不能为空", ex.getErrorMessage());
    }

    @Test
    void login_wrongVerificationCode_shouldFailBeforeRegister() {
        String phone = "13800138000";
        String code = "123456";
        UserLoginReqVO request = UserLoginReqVO.builder()
                .phone(phone)
                .code(code)
                .type(LoginTypeEnum.VERIFICATION_CODE.getValue())
                .build();

        when(verificationCodeService.consumeLoginVerificationCode(phone, code)).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> verificationCodeLoginStrategy.login(request));

        assertEquals(ResponseCodeEnum.VERIFICATION_CODE_ERROR.getErrorCode(), ex.getErrorCode());
        verify(userRpcService, never()).registerUser(phone);
    }

    @Test
    void login_registerFailAfterConsume_shouldThrowLoginFail() {
        String phone = "13800138001";
        String code = "654321";
        UserLoginReqVO request = UserLoginReqVO.builder()
                .phone(phone)
                .code(code)
                .type(LoginTypeEnum.VERIFICATION_CODE.getValue())
                .build();

        when(verificationCodeService.consumeLoginVerificationCode(phone, code)).thenReturn(true);
        when(userRpcService.registerUser(phone)).thenThrow(new BizException(ResponseCodeEnum.LOGIN_FAIL));

        BizException ex = assertThrows(BizException.class, () -> verificationCodeLoginStrategy.login(request));

        assertEquals(ResponseCodeEnum.LOGIN_FAIL.getErrorCode(), ex.getErrorCode());
    }
}
