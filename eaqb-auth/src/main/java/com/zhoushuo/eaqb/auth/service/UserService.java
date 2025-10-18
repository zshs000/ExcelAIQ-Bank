package com.zhoushuo.eaqb.auth.service;

import com.zhoushuo.eaqb.auth.modle.vo.user.UserLoginReqVO;
import com.zhoushuo.framework.commono.response.Response;

public interface UserService {

    /**
     * 登录与注册
     * @param userLoginReqVO
     * @return
     */
    Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO);
}