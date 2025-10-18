package com.zhoushuo.eaqb.auth.controller;

import com.zhoushuo.eaqb.auth.modle.vo.verificationcode.SendVerificationCodeReqVO;
import com.zhoushuo.eaqb.auth.service.VerificationCodeService;
import com.zhoushuo.framework.biz.operationlog.aspect.ApiOperationLog;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class VerificationCodeController {

    @Resource
    private VerificationCodeService verificationCodeService;

    @PostMapping("/verification/code/send")
    @ApiOperationLog(description = "发送短信验证码")
    public Response<?> send(@Validated @RequestBody SendVerificationCodeReqVO sendVerificationCodeReqVO) {
        return verificationCodeService.send(sendVerificationCodeReqVO);
    }

}