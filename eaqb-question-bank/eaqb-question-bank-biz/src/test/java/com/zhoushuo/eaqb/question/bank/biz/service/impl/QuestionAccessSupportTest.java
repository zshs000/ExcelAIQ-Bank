package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.enums.ResponseCodeEnum;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuestionAccessSupportTest {

    private final QuestionAccessSupport questionAccessSupport = new QuestionAccessSupport();

    @AfterEach
    void tearDown() {
        LoginUserContextHolder.remove();
    }

    @Test
    void requireCurrentUserId_shouldReturnContextUserId() {
        LoginUserContextHolder.setUserId(123L);

        Long userId = questionAccessSupport.requireCurrentUserId();

        assertEquals(123L, userId);
    }

    @Test
    void requireCurrentUserId_missingUserId_shouldThrowParamError() {
        BizException ex = assertThrows(BizException.class, questionAccessSupport::requireCurrentUserId);

        assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), ex.getErrorCode());
        assertEquals("请求头 userId 不能为空", ex.getErrorMessage());
    }

    @Test
    void requireCurrentUserId_invalidUserId_shouldThrowParamError() {
        LoginUserContextHolder.setUserId("abc");

        BizException ex = assertThrows(BizException.class, questionAccessSupport::requireCurrentUserId);

        assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), ex.getErrorCode());
        assertEquals("请求头 userId 必须是数字", ex.getErrorMessage());
    }
}
