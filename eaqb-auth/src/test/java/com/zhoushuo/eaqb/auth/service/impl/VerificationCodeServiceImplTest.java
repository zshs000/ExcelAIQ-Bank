package com.zhoushuo.eaqb.auth.service.impl;

import com.zhoushuo.eaqb.auth.config.RedisTemplateConfig;
import com.zhoushuo.eaqb.auth.constant.RedisKeyConstants;
import com.zhoushuo.eaqb.auth.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.auth.model.vo.verificationcode.SendVerificationCodeReqVO;
import com.zhoushuo.eaqb.auth.rpc.UserRpcService;
import com.zhoushuo.eaqb.user.dto.resp.CurrentUserCredentialRspDTO;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationCodeServiceImplTest {

    private static final String PHONE = "13800138000";
    private static final String IP = "192.0.2.10";

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private UserRpcService userRpcService;
    @InjectMocks
    private VerificationCodeServiceImpl verificationCodeService;
    @Captor
    private ArgumentCaptor<RedisScript<Long>> scriptCaptor;
    @Captor
    private ArgumentCaptor<List<String>> keysCaptor;
    @Captor
    private ArgumentCaptor<String> codeCaptor;
    @Captor
    private ArgumentCaptor<Long> deadlineCaptor;

    @AfterEach
    void tearDown() {
        LoginUserContextHolder.remove();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void send_shouldAcquireBothQuotasAndCreateCodeInOneScript() {
        attachRequest(IP);
        stubSendResult(1L);
        long earliestDeadline = endOfDayMillis();

        assertTrue(verificationCodeService.send(request()).isSuccess());

        captureSendScript();
        assertEquals(List.of(
                RedisKeyConstants.buildLoginVerificationCodeKey(PHONE),
                RedisKeyConstants.buildVerificationCodeDailyCountKey(PHONE),
                RedisKeyConstants.buildVerificationCodeIpDailyCountKey(IP)
        ), keysCaptor.getValue());
        assertTrue(codeCaptor.getValue().matches("[0-9]{6}"));
        assertEquals(Long.class, scriptCaptor.getValue().getResultType());
        assertTrue(deadlineCaptor.getValue() >= earliestDeadline);
        assertTrue(deadlineCaptor.getValue() <= endOfDayMillis());
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void send_withoutRequestContext_shouldPassOnlyCodeAndPhoneKeys() {
        stubSendResult(1L);

        assertTrue(verificationCodeService.send(request()).isSuccess());

        captureSendScript();
        assertEquals(List.of(
                RedisKeyConstants.buildLoginVerificationCodeKey(PHONE),
                RedisKeyConstants.buildVerificationCodeDailyCountKey(PHONE)
        ), keysCaptor.getValue());
    }

    @Test
    void sendPasswordUpdateCode_shouldUseDifferentCodeKeyButSameDailyQuotas() {
        attachRequest(IP);
        LoginUserContextHolder.setUserId(1L);
        when(userRpcService.getCurrentUserCredential()).thenReturn(
                CurrentUserCredentialRspDTO.builder().id(1L).phone(PHONE).build());
        stubSendResult(1L);

        assertTrue(verificationCodeService.sendPasswordUpdateCode().isSuccess());

        captureSendScript();
        assertEquals(List.of(
                RedisKeyConstants.buildPasswordUpdateVerificationCodeKey(PHONE),
                RedisKeyConstants.buildVerificationCodeDailyCountKey(PHONE),
                RedisKeyConstants.buildVerificationCodeIpDailyCountKey(IP)
        ), keysCaptor.getValue());
    }

    @ParameterizedTest
    @MethodSource("rejectedResults")
    void send_shouldMapScriptRejectionsAndFailClosedOnUnknownResult(Long result, ResponseCodeEnum expected) {
        stubSendResult(result);

        BizException exception = assertThrows(BizException.class,
                () -> verificationCodeService.send(request()));

        assertEquals(expected.getErrorCode(), exception.getErrorCode());
        verify(redisTemplate, never()).opsForValue();
    }

    static Stream<Arguments> rejectedResults() {
        return Stream.of(
                Arguments.of(-1L, ResponseCodeEnum.VERIFICATION_CODE_DAILY_LIMIT_EXCEEDED),
                Arguments.of(-2L, ResponseCodeEnum.VERIFICATION_CODE_IP_DAILY_LIMIT_EXCEEDED),
                Arguments.of(-3L, ResponseCodeEnum.VERIFICATION_CODE_SEND_FREQUENTLY),
                Arguments.of(null, ResponseCodeEnum.SYSTEM_ERROR),
                Arguments.of(0L, ResponseCodeEnum.SYSTEM_ERROR),
                Arguments.of(99L, ResponseCodeEnum.SYSTEM_ERROR)
        );
    }

    @Test
    void send_whenBlacklisted_shouldRejectBeforeExecutingScript() {
        String blacklistKey = RedisKeyConstants.buildVerificationCodeBlacklistKey(PHONE);
        when(redisTemplate.hasKey(blacklistKey)).thenReturn(true);

        BizException exception = assertThrows(BizException.class,
                () -> verificationCodeService.send(request()));

        assertEquals(ResponseCodeEnum.VERIFICATION_CODE_PHONE_IN_BLACKLIST.getErrorCode(), exception.getErrorCode());
        verify(redisTemplate).hasKey(blacklistKey);
        verifyNoMoreInteractions(redisTemplate);
    }

    @Test
    void send_whenRedisFails_shouldNotReturnSuccessOrRetryTheWrite() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        RedisConnectionFailureException failure = new RedisConnectionFailureException("test failure");
        when(redisTemplate.execute(any(RedisScript.class), anyList(),
                anyString(), eq(10), eq(50), eq(180000L), anyLong())).thenThrow(failure);

        assertSame(failure, assertThrows(RedisConnectionFailureException.class,
                () -> verificationCodeService.send(request())));

        captureSendScript();
        verify(redisTemplate).hasKey(RedisKeyConstants.buildVerificationCodeBlacklistKey(PHONE));
        verifyNoMoreInteractions(redisTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void send_shouldPreserveJsonCodeEncodingAndUseNumericScriptArguments() {
        stubSendResult(1L);
        verificationCodeService.send(request());
        captureSendScript();
        RedisTemplate<String, Object> configuredTemplate = new RedisTemplateConfig()
                .redisTemplate(mock(RedisConnectionFactory.class));
        RedisSerializer<Object> serializer = (RedisSerializer<Object>) configuredTemplate.getValueSerializer();

        assertEquals("\"" + codeCaptor.getValue() + "\"", serialized(serializer, codeCaptor.getValue()));
        assertEquals("10", serialized(serializer, 10));
        assertEquals("50", serialized(serializer, 50));
        assertEquals("180000", serialized(serializer, 180000L));
        assertEquals(deadlineCaptor.getValue().toString(), serialized(serializer, deadlineCaptor.getValue()));
    }

    @Test
    void sendPasswordUpdateCode_whenNotLoggedIn_shouldFailBeforeCallingDependencies() {
        BizException exception = assertThrows(BizException.class,
                () -> verificationCodeService.sendPasswordUpdateCode());

        assertEquals(ResponseCodeEnum.UNAUTHORIZED.getErrorCode(), exception.getErrorCode());
        verifyNoInteractions(userRpcService, redisTemplate);
    }

    @SuppressWarnings("unchecked")
    private void stubSendResult(Long result) {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(redisTemplate.execute(any(RedisScript.class), anyList(),
                anyString(), eq(10), eq(50), eq(180000L), anyLong())).thenReturn(result);
    }

    private void captureSendScript() {
        verify(redisTemplate).execute(scriptCaptor.capture(), keysCaptor.capture(),
                codeCaptor.capture(), eq(10), eq(50), eq(180000L), deadlineCaptor.capture());
    }

    private SendVerificationCodeReqVO request() {
        return SendVerificationCodeReqVO.builder().phone(PHONE).build();
    }

    private void attachRequest(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", ip);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private long endOfDayMillis() {
        return LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1;
    }

    private String serialized(RedisSerializer<Object> serializer, Object value) {
        return new String(serializer.serialize(value), StandardCharsets.UTF_8);
    }
}
