package com.zhoushuo.eaqb.auth.service;

import com.zhoushuo.eaqb.auth.modle.vo.verificationcode.SendVerificationCodeReqVO;
import com.zhoushuo.framework.commono.response.Response;

public interface VerificationCodeService {

    /**
     * 发送短信验证码
     *
     * @param sendVerificationCodeReqVO
     * @return
     */
    Response<?> send(SendVerificationCodeReqVO sendVerificationCodeReqVO);
}