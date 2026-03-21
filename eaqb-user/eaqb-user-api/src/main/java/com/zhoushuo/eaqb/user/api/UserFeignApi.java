package com.zhoushuo.eaqb.user.api;

import com.zhoushuo.eaqb.user.dto.req.FindUserByPhoneReqDTO;
import com.zhoushuo.eaqb.user.dto.req.RegisterUserReqDTO;
import com.zhoushuo.eaqb.user.constant.ApiConstants;
import com.zhoushuo.eaqb.user.dto.req.UpdateUserPasswordReqDTO;
import com.zhoushuo.eaqb.user.dto.resp.CurrentUserCredentialRspDTO;
import com.zhoushuo.eaqb.user.dto.resp.FindUserByPhoneRspDTO;
import com.zhoushuo.framework.commono.response.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface UserFeignApi {

    String PREFIX = "/user";

    /**
     * 用户注册
     *
     * @param registerUserReqDTO
     * @return
     */
    @PostMapping(value = PREFIX + "/register")
    Response<Long> registerUser(@RequestBody RegisterUserReqDTO registerUserReqDTO);

    /**
     * 根据手机号查询用户信息
     *
     * @param findUserByPhoneReqDTO
     * @return
     */
    @PostMapping(value = PREFIX + "/findByPhone")
    Response<FindUserByPhoneRspDTO> findByPhone(@RequestBody FindUserByPhoneReqDTO findUserByPhoneReqDTO);

    /**
     * 查询当前登录用户手机号
     *
     * @return
     */
    @PostMapping(value = PREFIX + "/credential/current")
    Response<CurrentUserCredentialRspDTO> getCurrentUserCredential();

    /**
     * 更新密码
     *
     * @param updateUserPasswordReqDTO
     * @return
     */
    @PostMapping(value = PREFIX + "/password/update")
    Response<?> updatePassword(@RequestBody UpdateUserPasswordReqDTO updateUserPasswordReqDTO);
}
