package com.zhoushuo.eaqb.auth.service;

import com.zhoushuo.eaqb.auth.model.vo.user.UpdatePasswordReqVO;
import com.zhoushuo.eaqb.auth.model.vo.user.UserLoginReqVO;
import com.zhoushuo.framework.common.response.Response;

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