package com.zhoushuo.eaqb.auth.service.strategy.impl;

import com.zhoushuo.eaqb.auth.constant.RedisKeyConstants;
import com.zhoushuo.eaqb.auth.enums.LoginTypeEnum;
import com.zhoushuo.eaqb.auth.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.auth.modle.vo.user.UserLoginReqVO;
import com.zhoushuo.eaqb.auth.rpc.UserRpcService;
import com.zhoushuo.framework.commono.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationCodeLoginStrategyTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private UserRpcService userRpcService;

    @InjectMocks
    private VerificationCodeLoginStrategy verificationCodeLoginStrategy;

    @Test
    void login_wrongVerificationCode_shouldFailBeforeRegister() {
        String phone = "13800138000";
        String code = "123456";
        String key = RedisKeyConstants.buildVerificationCodeKey(phone);
        UserLoginReqVO request = UserLoginReqVO.builder()
                .phone(phone)
                .code(code)
                .type(LoginTypeEnum.VERIFICATION_CODE.getValue())
                .build();

        when(redisTemplate.execute(any(), eq(java.util.Collections.singletonList(key)), eq(code))).thenReturn(0L);

        BizException ex = assertThrows(BizException.class, () -> verificationCodeLoginStrategy.login(request));

        assertEquals(ResponseCodeEnum.VERIFICATION_CODE_ERROR.getErrorCode(), ex.getErrorCode());
        verify(userRpcService, never()).registerUser(phone);
    }

    @Test
    void login_registerFailAfterConsume_shouldThrowLoginFail() {
        String phone = "13800138001";
        String code = "654321";
        String key = RedisKeyConstants.buildVerificationCodeKey(phone);
        UserLoginReqVO request = UserLoginReqVO.builder()
                .phone(phone)
                .code(code)
                .type(LoginTypeEnum.VERIFICATION_CODE.getValue())
                .build();

        when(redisTemplate.execute(any(), eq(java.util.Collections.singletonList(key)), eq(code))).thenReturn(1L);
        when(userRpcService.registerUser(phone)).thenThrow(new BizException(ResponseCodeEnum.LOGIN_FAIL));

        BizException ex = assertThrows(BizException.class, () -> verificationCodeLoginStrategy.login(request));

        assertEquals(ResponseCodeEnum.LOGIN_FAIL.getErrorCode(), ex.getErrorCode());
    }
}
