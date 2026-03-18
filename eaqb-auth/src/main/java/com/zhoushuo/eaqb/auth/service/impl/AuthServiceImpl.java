package com.zhoushuo.eaqb.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.zhoushuo.eaqb.auth.modle.vo.user.UpdatePasswordReqVO;
import com.zhoushuo.eaqb.auth.rpc.UserRpcService;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.eaqb.auth.modle.vo.user.UserLoginReqVO;
import com.zhoushuo.eaqb.auth.service.AuthService;
import com.zhoushuo.eaqb.auth.service.factory.LoginStrategyFactory;
import com.zhoushuo.eaqb.auth.service.strategy.LoginStrategy;
import com.zhoushuo.framework.commono.response.Response;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class AuthServiceImpl implements AuthService {
    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private UserRpcService userRpcService;

    @Resource
    private LoginStrategyFactory loginStrategyFactory;

    /**
     * 登录与注册
     *
     * @param userLoginReqVO
     * @return
     */
    @Override
    public Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO) {
        LoginStrategy loginStrategy = loginStrategyFactory.getStrategy(userLoginReqVO.getType());
        Long userId = loginStrategy.login(userLoginReqVO);

        // SaToken 登录用户，并返回 token 令牌
        // SaToken 登录用户, 入参为用户 ID
        StpUtil.login(userId);

        // 获取 Token 令牌
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();

        // 返回 Token 令牌
        return Response.success(tokenInfo.tokenValue);
    }

    /**
     * 退出登录接口
     *
     * @param
     * @return
     */
    @Override
    public Response<?> logout(){
        Long userId = LoginUserContextHolder.getUserId();

        log.info("==> 用户退出登录, userId: {}", userId);

        // 退出登录 (指定用户 ID)
        StpUtil.logout(userId);

        return Response.success();
    }

    /**
     * 修改密码
     *
     * @param updatePasswordReqVO
     * @return
     */
    @Override
    public Response<?> updatePassword(UpdatePasswordReqVO updatePasswordReqVO) {
        // 新密码
        String newPassword = updatePasswordReqVO.getNewPassword();
        // 密码加密
        String encodePassword = passwordEncoder.encode(newPassword);

        // RPC: 调用用户服务：更新密码
        userRpcService.updatePassword(encodePassword);

        return Response.success();
    }
}
