package com.zhoushuo.eaqb.auth.rpc;

import com.zhoushuo.eaqb.user.api.UserFeignApi;
import com.zhoushuo.eaqb.user.dto.req.FindUserByPhoneReqDTO;
import com.zhoushuo.eaqb.user.dto.req.RegisterUserReqDTO;
import com.zhoushuo.eaqb.user.dto.req.UpdateUserPasswordReqDTO;
import com.zhoushuo.eaqb.user.dto.resp.CurrentUserCredentialRspDTO;
import com.zhoushuo.eaqb.user.dto.resp.FindUserByPhoneRspDTO;
import feign.Request;
import feign.RetryableException;
import com.zhoushuo.framework.common.exception.BizException;
import com.zhoushuo.framework.common.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;

@ExtendWith(MockitoExtension.class)
class UserRpcServiceTest {

    @Mock
    private UserFeignApi userFeignApi;

    @InjectMocks
    private UserRpcService userRpcService;

    @Test
    void findUserByPhone_userNotFound_shouldReturnNull() {
        Response<FindUserByPhoneRspDTO> response = Response.fail("USER-20008", "该用户不存在");
        when(userFeignApi.findByPhone(any(FindUserByPhoneReqDTO.class))).thenReturn(response);

        FindUserByPhoneRspDTO result = userRpcService.findUserByPhone("13800138000");

        assertNull(result);
    }

    @Test
    void findUserByPhone_otherBizError_shouldThrowBizException() {
        Response<FindUserByPhoneRspDTO> response = Response.fail("USER-10000", "出错啦，后台小哥正在努力修复中...");
        when(userFeignApi.findByPhone(any(FindUserByPhoneReqDTO.class))).thenReturn(response);

        BizException ex = assertThrows(BizException.class, () -> userRpcService.findUserByPhone("13800138000"));

        assertEquals("USER-10000", ex.getErrorCode());
        assertEquals("出错啦，后台小哥正在努力修复中...", ex.getErrorMessage());
    }

    @Test
    void registerUser_otherBizError_shouldThrowBizException() {
        Response<Long> response = Response.fail("USER-10000", "出错啦，后台小哥正在努力修复中...");
        when(userFeignApi.registerUser(any(RegisterUserReqDTO.class))).thenReturn(response);

        BizException ex = assertThrows(BizException.class, () -> userRpcService.registerUser("13800138000"));

        assertEquals("USER-10000", ex.getErrorCode());
        assertEquals("出错啦，后台小哥正在努力修复中...", ex.getErrorMessage());
    }

    @Test
    void registerUser_retryableExceptionThenSuccess_shouldRetryAndReturnUserId() {
        RetryableException retryableException = new RetryableException(
                503,
                "temporary network error",
                Request.HttpMethod.POST,
                new Date(),
                Request.create(Request.HttpMethod.POST, "/internal/user/register", Collections.emptyMap(), null, StandardCharsets.UTF_8)
        );
        when(userFeignApi.registerUser(any(RegisterUserReqDTO.class)))
                .thenThrow(retryableException)
                .thenReturn(Response.success(1001L));

        Long userId = userRpcService.registerUser("13800138000");

        assertEquals(1001L, userId);
        verify(userFeignApi, times(2)).registerUser(any(RegisterUserReqDTO.class));
    }

    @Test
    void registerUser_retryableExceptionAfterMaxAttempts_shouldThrowFriendlyBizException() {
        RetryableException retryableException = new RetryableException(
                503,
                "temporary network error",
                Request.HttpMethod.POST,
                new Date(),
                Request.create(Request.HttpMethod.POST, "/internal/user/register", Collections.emptyMap(), null, StandardCharsets.UTF_8)
        );
        when(userFeignApi.registerUser(any(RegisterUserReqDTO.class)))
                .thenThrow(retryableException);

        BizException ex = assertThrows(BizException.class, () -> userRpcService.registerUser("13800138000"));

        assertEquals("AUTH-RPC-500", ex.getErrorCode());
        assertEquals("用户服务暂时不可用，请稍后重试", ex.getErrorMessage());
        verify(userFeignApi, times(3)).registerUser(any(RegisterUserReqDTO.class));
    }

    @Test
    void getCurrentUserCredential_success_shouldReturnPhone() {
        CurrentUserCredentialRspDTO currentUserPhoneRspDTO = CurrentUserCredentialRspDTO.builder()
                .id(1001L)
                .phone("13800138000")
                .build();
        when(userFeignApi.getCurrentUserCredential()).thenReturn(Response.success(currentUserPhoneRspDTO));

        CurrentUserCredentialRspDTO result = userRpcService.getCurrentUserCredential();

        assertEquals("13800138000", result.getPhone());
    }

    @Test
    void updatePassword_downstreamBizError_shouldThrowBizException() {
        when(userFeignApi.updatePassword(any(UpdateUserPasswordReqDTO.class)))
                .thenReturn(Response.fail("USER-20009", "密码修改失败"));

        BizException ex = assertThrows(BizException.class, () -> userRpcService.updatePassword("new-password"));

        assertEquals("USER-20009", ex.getErrorCode());
        assertEquals("密码修改失败", ex.getErrorMessage());
    }

    @Test
    void updatePassword_emptyResponse_shouldThrowBizException() {
        when(userFeignApi.updatePassword(any(UpdateUserPasswordReqDTO.class))).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> userRpcService.updatePassword("new-password"));

        assertEquals("AUTH-RPC-500", ex.getErrorCode());
        assertEquals("用户服务响应为空", ex.getErrorMessage());
    }
}
