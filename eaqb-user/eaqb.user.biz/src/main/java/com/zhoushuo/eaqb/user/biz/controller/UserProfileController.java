package com.zhoushuo.eaqb.user.biz.controller;

import com.zhoushuo.eaqb.user.biz.model.vo.UpdateUserInfoReqVO;
import com.zhoushuo.eaqb.user.biz.service.UserService;
import com.zhoushuo.eaqb.user.dto.resp.CurrentUserProfileRspDTO;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@Slf4j
public class UserProfileController {

    @Resource
    private UserService userService;

    /**
     * 用户信息修改
     *
     * @param updateUserInfoReqVO
     * @return
     */
//    @ApiOperationLog(description = "用户信息修改")
    @PostMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<?> updateUserInfo(@Validated UpdateUserInfoReqVO updateUserInfoReqVO) {
        return userService.updateUserInfo(updateUserInfoReqVO);
    }

    @GetMapping("/profile/current")
    public Response<CurrentUserProfileRspDTO> getCurrentUserProfile() {
        return userService.getCurrentUserProfile();
    }
}
