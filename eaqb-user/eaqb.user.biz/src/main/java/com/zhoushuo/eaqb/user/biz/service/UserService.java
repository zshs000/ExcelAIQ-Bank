package com.zhoushuo.eaqb.user.biz.service;

import com.zhoushuo.eaqb.user.biz.model.vo.UpdateUserInfoReqVO;
import com.zhoushuo.framework.commono.response.Response;

public interface UserService {

    /**
     * 更新用户信息
     *
     * @param updateUserInfoReqVO
     * @return
     */
    Response<?> updateUserInfo(UpdateUserInfoReqVO updateUserInfoReqVO);
}