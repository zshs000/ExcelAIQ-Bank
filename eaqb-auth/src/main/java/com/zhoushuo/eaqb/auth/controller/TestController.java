package com.zhoushuo.eaqb.auth.controller;

import com.zhoushuo.framework.biz.operationlog.aspect.ApiOperationLog;
import com.zhoushuo.framework.commono.response.Response;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
public class TestController {

    @GetMapping("/test")
    @ApiOperationLog(description = "测试接口")
    public Response<String> test() {
        return Response.success("Hello, zhoushuo");
    }

    @PostMapping("/test2")
    @ApiOperationLog(description = "测试接口2")
    public Response<User> test2(@RequestBody @Validated User user) {

        return Response.success(user);
    }
}