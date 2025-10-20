package com.zhoushuo.eaqb.auth.service.impl;


import cn.hutool.core.util.RandomUtil;
import com.zhoushuo.eaqb.auth.constant.RedisKeyConstants;
import com.zhoushuo.eaqb.auth.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.auth.modle.vo.verificationcode.SendVerificationCodeReqVO;
import com.zhoushuo.eaqb.auth.service.VerificationCodeService;
import com.zhoushuo.eaqb.auth.sms.AliyunSmsHelper;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class VerificationCodeServiceImpl implements VerificationCodeService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private ThreadPoolTaskExecutor taskExecutor;
    @Resource
    private AliyunSmsHelper aliyunSmsHelper;

    /**
     * 发送短信验证码
     *
     * @param sendVerificationCodeReqVO
     * @return
     */
    @Override
    public Response<?> send(SendVerificationCodeReqVO sendVerificationCodeReqVO) {
        // 手机号
        String phone = sendVerificationCodeReqVO.getPhone();

        // 构建验证码 redis key
        String key = RedisKeyConstants.buildVerificationCodeKey(phone);

        // 判断是否已发送验证码
        boolean isSent = redisTemplate.hasKey(key);
        if (isSent) {
            // 若之前发送的验证码未过期，则提示发送频繁
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_SEND_FREQUENTLY);
        }

        // 生成 6 位随机数字验证码
        String verificationCode = RandomUtil.randomNumbers(6);


        log.info("==> 手机号: {}, 已生成验证码：【{}】", phone, verificationCode);

         //调用第三方短信发送服务
//        taskExecutor.submit(() -> {
//            String signName = "速通互联验证码"; // 签名，个人测试签名无法修改
//            String templateCode = "100001"; // 短信模板编码
//            // 短信模板参数，code 表示要发送的验证码；min 表示验证码有时间时长，即 3 分钟
//            String templateParam = String.format("{\"code\":\"%s\",\"min\":\"3\"}", verificationCode);
//            aliyunSmsHelper.sendMessage(signName, templateCode, phone, templateParam);
//        });

        // 存储验证码到 redis, 并设置过期时间为 3 分钟
        redisTemplate.opsForValue().set(key, verificationCode, 3, TimeUnit.MINUTES);

        return Response.success();
    }
}
