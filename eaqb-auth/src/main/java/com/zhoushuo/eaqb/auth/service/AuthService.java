package com.zhoushuo.eaqb.auth.service;

import com.zhoushuo.eaqb.auth.modle.vo.user.UpdatePasswordReqVO;
import com.zhoushuo.eaqb.auth.modle.vo.user.UserLoginReqVO;
import com.zhoushuo.framework.commono.response.Response;

public interface AuthService {

    /**
     * 登录与注册
     * @param userLoginReqVO
     * @return
     */
    Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO);

    /**
     * 退出登录
     *
     * @return
     */
    Response<?> logout();

    Response<?> updatePassword(UpdatePasswordReqVO updatePasswordReqVO);
}