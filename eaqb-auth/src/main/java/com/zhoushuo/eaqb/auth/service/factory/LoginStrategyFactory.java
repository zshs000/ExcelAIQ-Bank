package com.zhoushuo.eaqb.auth.service.factory;

import com.zhoushuo.eaqb.auth.enums.LoginTypeEnum;
import com.zhoushuo.eaqb.auth.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.auth.service.strategy.LoginStrategy;
import com.zhoushuo.framework.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 根据登录类型分发到对应登录策略。
 */
@Component
public class LoginStrategyFactory {

    private final Map<LoginTypeEnum, LoginStrategy> strategyMap = new EnumMap<>(LoginTypeEnum.class);

    public LoginStrategyFactory(List<LoginStrategy> loginStrategies) {
        for (LoginStrategy loginStrategy : loginStrategies) {
            strategyMap.put(loginStrategy.getLoginType(), loginStrategy);
        }
    }

    public LoginStrategy getStrategy(Integer loginType) {
        LoginTypeEnum loginTypeEnum = LoginTypeEnum.valueOf(loginType);
        if (loginTypeEnum == null) {
            throw new BizException(ResponseCodeEnum.LOGIN_TYPE_ERROR);
        }

        LoginStrategy loginStrategy = strategyMap.get(loginTypeEnum);
        if (loginStrategy == null) {
            throw new BizException(ResponseCodeEnum.LOGIN_TYPE_ERROR);
        }
        return loginStrategy;
    }
}
