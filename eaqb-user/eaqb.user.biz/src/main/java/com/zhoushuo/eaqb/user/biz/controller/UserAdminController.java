package com.zhoushuo.eaqb.user.biz.controller;

import com.zhoushuo.eaqb.user.biz.service.UserService;
import com.zhoushuo.eaqb.user.dto.resp.AdminUserListRspDTO;
import com.zhoushuo.framework.biz.operationlog.aspect.ApiOperationLog;
import com.zhoushuo.framework.common.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user/admin")
@Slf4j
public class UserAdminController {

    @Resource
    private UserService userService;

    @GetMapping("/list")
    @ApiOperationLog(description = "管理员查看全局用户列表")
    public Response<List<AdminUserListRspDTO>> listUsersForAdmin() {
        return userService.listUsersForAdmin();
    }
}
