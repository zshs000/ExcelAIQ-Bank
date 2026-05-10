package com.zhoushuo.eaqb.auth.service.factory;

import com.zhoushuo.eaqb.auth.enums.LoginTypeEnum;
import com.zhoushuo.eaqb.auth.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.auth.service.strategy.LoginStrategy;
import com.zhoushuo.framework.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginStrategyFactoryTest {

    @Test
    void getStrategy_shouldReturnMatchedStrategy() {
        LoginStrategy strategy = mock(LoginStrategy.class);
        when(strategy.getLoginType()).thenReturn(LoginTypeEnum.VERIFICATION_CODE);
        LoginStrategyFactory factory = new LoginStrategyFactory(List.of(strategy));

        LoginStrategy result = factory.getStrategy(LoginTypeEnum.VERIFICATION_CODE.getValue());

        assertSame(strategy, result);
    }

    @Test
    void getStrategy_invalidType_shouldThrowBizException() {
        LoginStrategyFactory factory = new LoginStrategyFactory(List.of());

        BizException ex = assertThrows(BizException.class, () -> factory.getStrategy(999));

        assertEquals(ResponseCodeEnum.LOGIN_TYPE_ERROR.getErrorCode(), ex.getErrorCode());
    }
}
