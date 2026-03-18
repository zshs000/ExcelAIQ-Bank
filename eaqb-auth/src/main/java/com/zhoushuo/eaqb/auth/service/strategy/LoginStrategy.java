package com.zhoushuo.eaqb.auth.service.strategy;

import com.zhoushuo.eaqb.auth.enums.LoginTypeEnum;
import com.zhoushuo.eaqb.auth.modle.vo.user.UserLoginReqVO;

/**
 * 不同登录方式的统一抽象。
 */
public interface LoginStrategy {

    /**
     * 当前策略支持的登录类型。
     */
    LoginTypeEnum getLoginType();

    /**
     * 执行登录校验并返回登录用户 ID。
     */
    Long login(UserLoginReqVO userLoginReqVO);
}
