package com.zhoushuo.eaqb.auth.service.impl;

import com.zhoushuo.eaqb.auth.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.auth.modle.vo.user.UpdatePasswordReqVO;
import com.zhoushuo.eaqb.auth.modle.vo.user.UserLoginReqVO;
import com.zhoushuo.eaqb.auth.rpc.UserRpcService;
import com.zhoushuo.eaqb.auth.service.VerificationCodeService;
import com.zhoushuo.eaqb.auth.service.factory.LoginStrategyFactory;
import com.zhoushuo.eaqb.user.dto.resp.CurrentUserCredentialRspDTO;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRpcService userRpcService;
    @Mock
    private LoginStrategyFactory loginStrategyFactory;
    @Mock
    private VerificationCodeService verificationCodeService;

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

    @Test
    void updatePassword_wrongVerificationCode_shouldThrowVerificationCodeError() {
        LoginUserContextHolder.setUserId(1001L);
        try {
            CurrentUserCredentialRspDTO currentUserPhoneRspDTO = CurrentUserCredentialRspDTO.builder()
                    .id(1001L)
                    .phone("13800138000")
                    .build();
            UpdatePasswordReqVO request = UpdatePasswordReqVO.builder()
                    .code("123456")
                    .newPassword("new-password")
                    .build();
            when(userRpcService.getCurrentUserCredential()).thenReturn(currentUserPhoneRspDTO);
            when(verificationCodeService.consumePasswordUpdateVerificationCode("13800138000", "123456"))
                    .thenReturn(false);

            BizException ex = assertThrows(BizException.class, () -> authService.updatePassword(request));

            assertEquals(ResponseCodeEnum.VERIFICATION_CODE_ERROR.getErrorCode(), ex.getErrorCode());
        } finally {
            LoginUserContextHolder.remove();
        }
    }

    @Test
    void updatePassword_userRpcFailed_shouldPropagateException() {
        LoginUserContextHolder.setUserId(1001L);
        try {
            CurrentUserCredentialRspDTO currentUserPhoneRspDTO = CurrentUserCredentialRspDTO.builder()
                    .id(1001L)
                    .phone("13800138000")
                    .build();
            UpdatePasswordReqVO request = UpdatePasswordReqVO.builder()
                    .code("123456")
                    .newPassword("new-password")
                    .build();
            when(userRpcService.getCurrentUserCredential()).thenReturn(currentUserPhoneRspDTO);
            when(verificationCodeService.consumePasswordUpdateVerificationCode("13800138000", "123456"))
                    .thenReturn(true);
            BizException downstreamEx = new BizException("USER-20009", "密码修改失败");
            org.mockito.Mockito.doThrow(downstreamEx).when(userRpcService).updatePassword("new-password");

            BizException ex = assertThrows(BizException.class, () -> authService.updatePassword(request));

            assertEquals("USER-20009", ex.getErrorCode());
            verify(userRpcService).updatePassword("new-password");
            verify(verificationCodeService).consumePasswordUpdateVerificationCode("13800138000", "123456");
        } finally {
            LoginUserContextHolder.remove();
        }
    }
}
