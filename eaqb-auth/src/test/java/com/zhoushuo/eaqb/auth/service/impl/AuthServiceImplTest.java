package com.zhoushuo.eaqb.auth.service.impl;

import com.zhoushuo.eaqb.auth.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.auth.modle.vo.user.UserLoginReqVO;
import com.zhoushuo.eaqb.auth.rpc.UserRpcService;
import com.zhoushuo.eaqb.auth.service.factory.LoginStrategyFactory;
import com.zhoushuo.framework.commono.exception.BizException;
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
class AuthServiceImplTest {

    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserRpcService userRpcService;
    @Mock
    private LoginStrategyFactory loginStrategyFactory;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void loginAndRegister_invalidType_shouldThrowLoginTypeError() {
        UserLoginReqVO request = UserLoginReqVO.builder()
                .phone("13800138000")
                .type(999)
                .build();

        when(loginStrategyFactory.getStrategy(999)).thenThrow(new BizException(ResponseCodeEnum.LOGIN_TYPE_ERROR));

        BizException ex = assertThrows(BizException.class, () -> authService.loginAndRegister(request));

        assertEquals(ResponseCodeEnum.LOGIN_TYPE_ERROR.getErrorCode(), ex.getErrorCode());
    }
}
