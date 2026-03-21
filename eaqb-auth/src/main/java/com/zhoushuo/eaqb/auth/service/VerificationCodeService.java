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

    /**
     * 发送修改密码验证码
     * @return
     */
    Response<?> sendPasswordUpdateCode();

    /**
     * 消费登录验证码
     *
     * @param phone
     * @param verificationCode
     * @return
     */
    boolean consumeLoginVerificationCode(String phone, String verificationCode);

    /**
     * 消费修改密码验证码
     *
     * @param phone
     * @param verificationCode
     * @return
     */
    boolean consumePasswordUpdateVerificationCode(String phone, String verificationCode);
}
