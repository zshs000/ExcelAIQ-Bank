package com.zhoushuo.eaqb.auth.service.impl;

import com.zhoushuo.eaqb.auth.constant.RedisKeyConstants;
import com.zhoushuo.eaqb.auth.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.auth.modle.vo.verificationcode.SendVerificationCodeReqVO;
import com.zhoushuo.eaqb.auth.rpc.UserRpcService;
import com.zhoushuo.eaqb.auth.sms.AliyunSmsHelper;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationCodeServiceImplTest {

    private static final String LOGIN_REQUIRED_ERROR_CODE = "AUTH-401";

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private ThreadPoolTaskExecutor taskExecutor;
    @Mock
    private AliyunSmsHelper aliyunSmsHelper;
    @Mock
    private UserRpcService userRpcService;

    @InjectMocks
    private VerificationCodeServiceImpl verificationCodeService;

    @AfterEach
    void tearDown() {
        LoginUserContextHolder.remove();
    }

    @Test
    void send_shouldUseAtomicSetIfAbsentForCooldownWindow() {
        String phone = "13800138000";
        String verificationCodeKey = RedisKeyConstants.buildLoginVerificationCodeKey(phone);
        SendVerificationCodeReqVO request = SendVerificationCodeReqVO.builder()
                .phone(phone)
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey(RedisKeyConstants.buildVerificationCodeBlacklistKey(phone))).thenReturn(false);
        when(valueOperations.get(RedisKeyConstants.buildVerificationCodeDailyCountKey(phone))).thenReturn(null);
        when(valueOperations.setIfAbsent(eq(verificationCodeKey), anyString(), eq(3L), eq(TimeUnit.MINUTES)))
                .thenReturn(true);
        when(valueOperations.increment(RedisKeyConstants.buildVerificationCodeDailyCountKey(phone))).thenReturn(1L);
        when(redisTemplate.expireAt(eq(RedisKeyConstants.buildVerificationCodeDailyCountKey(phone)), org.mockito.ArgumentMatchers.any(Date.class)))
                .thenReturn(true);

        Response<?> response = verificationCodeService.send(request);

        assertTrue(response.isSuccess());
        verify(valueOperations).setIfAbsent(eq(verificationCodeKey), anyString(), eq(3L), eq(TimeUnit.MINUTES));
        verify(redisTemplate, never()).hasKey(verificationCodeKey);
        verify(valueOperations, never()).set(eq(verificationCodeKey), anyString(), eq(3L), eq(TimeUnit.MINUTES));
    }

    @Test
    void send_whenCooldownKeyAlreadyExists_shouldThrowFrequentlyError() {
        String phone = "13800138000";
        String verificationCodeKey = RedisKeyConstants.buildLoginVerificationCodeKey(phone);
        SendVerificationCodeReqVO request = SendVerificationCodeReqVO.builder()
                .phone(phone)
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey(RedisKeyConstants.buildVerificationCodeBlacklistKey(phone))).thenReturn(false);
        when(valueOperations.get(RedisKeyConstants.buildVerificationCodeDailyCountKey(phone))).thenReturn(null);
        when(valueOperations.setIfAbsent(eq(verificationCodeKey), anyString(), eq(3L), eq(TimeUnit.MINUTES)))
                .thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> verificationCodeService.send(request));

        assertEquals(ResponseCodeEnum.VERIFICATION_CODE_SEND_FREQUENTLY.getErrorCode(), ex.getErrorCode());
        verify(valueOperations, never()).set(eq(verificationCodeKey), anyString(), eq(3L), eq(TimeUnit.MINUTES));
    }

    @Test
    void sendPasswordUpdateCode_whenNotLoggedIn_shouldFailFastBeforeCallingUserService() {
        BizException ex = assertThrows(BizException.class, () -> verificationCodeService.sendPasswordUpdateCode());

        assertEquals(LOGIN_REQUIRED_ERROR_CODE, ex.getErrorCode());
        verifyNoInteractions(userRpcService);
    }
}
