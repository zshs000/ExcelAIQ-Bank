package com.zhoushuo.eaqb.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.zhoushuo.eaqb.auth.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.auth.model.vo.user.UpdatePasswordReqVO;
import com.zhoushuo.eaqb.auth.rpc.UserRpcService;
import com.zhoushuo.eaqb.auth.service.VerificationCodeService;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.eaqb.auth.model.vo.user.UserLoginReqVO;
import com.zhoushuo.eaqb.auth.service.AuthService;
import com.zhoushuo.eaqb.auth.service.factory.LoginStrategyFactory;
import com.zhoushuo.eaqb.auth.service.strategy.LoginStrategy;
import com.zhoushuo.eaqb.user.dto.resp.CurrentUserCredentialRspDTO;
import com.zhoushuo.framework.common.exception.BizException;
import com.zhoushuo.framework.common.response.Response;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class AuthServiceImpl implements AuthService {
    @Resource
    private UserRpcService userRpcService;

    @Resource
    private LoginStrategyFactory loginStrategyFactory;

    @Resource
    private VerificationCodeService verificationCodeService;

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
        Long userId = LoginUserContextHolder.getUserId();
        CurrentUserCredentialRspDTO currentUserPhone = userRpcService.getCurrentUserCredential();

        if (!verificationCodeService.consumePasswordUpdateVerificationCode(
                currentUserPhone.getPhone(),
                updatePasswordReqVO.getCode()
        )) {
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
        }

        // RPC: 调用用户服务，用户服务内部负责密码加密与落库
        userRpcService.updatePassword(updatePasswordReqVO.getNewPassword());

        // 修改密码属于安全敏感操作，成功后强制当前账号的旧登录态失效。
        // 这里使用 kickout 而不是 logout，让旧 token 在后续访问时明确表现为“被踢下线”。
        StpUtil.kickout(userId);

        return Response.success();
    }
}
