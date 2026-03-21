package com.zhoushuo.eaqb.user.biz.service;

import com.zhoushuo.eaqb.user.dto.req.FindUserByPhoneReqDTO;
import com.zhoushuo.eaqb.user.dto.req.RegisterUserReqDTO;
import com.zhoushuo.eaqb.user.biz.model.vo.UpdateUserInfoReqVO;
import com.zhoushuo.eaqb.user.dto.req.UpdateUserPasswordReqDTO;
import com.zhoushuo.eaqb.user.dto.resp.AdminUserListRspDTO;
import com.zhoushuo.eaqb.user.dto.resp.FindUserByPhoneRspDTO;
import com.zhoushuo.framework.commono.response.Response;

import java.util.List;

public interface UserService {

    /**
     * 更新用户信息
     *
     * @param updateUserInfoReqVO
     * @return
     */
    Response<?> updateUserInfo(UpdateUserInfoReqVO updateUserInfoReqVO);

    /**
     * 用户注册
     *
     * @param registerUserReqDTO
     * @return
     */
    Response<Long> register(RegisterUserReqDTO registerUserReqDTO);

    /**
     * 根据手机号查询用户信息
     *
     * @param findUserByPhoneReqDTO
     * @return
     */
    Response<FindUserByPhoneRspDTO> findByPhone(FindUserByPhoneReqDTO findUserByPhoneReqDTO);

    /**
     * 更新密码
     *
     * @param updateUserPasswordReqDTO
     * @return
     */
    Response<?> updatePassword(UpdateUserPasswordReqDTO updateUserPasswordReqDTO);

    /**
     * 管理员查看全局用户列表
     *
     * @return
     */
    Response<List<AdminUserListRspDTO>> listUsersForAdmin();
}
