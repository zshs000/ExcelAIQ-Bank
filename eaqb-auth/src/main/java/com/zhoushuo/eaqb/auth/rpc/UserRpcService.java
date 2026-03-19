package com.zhoushuo.eaqb.auth.rpc;

import com.zhoushuo.eaqb.user.dto.req.FindUserByPhoneReqDTO;
import com.zhoushuo.eaqb.user.dto.req.RegisterUserReqDTO;
import com.zhoushuo.eaqb.user.api.UserFeignApi;
import com.zhoushuo.eaqb.user.dto.req.UpdateUserPasswordReqDTO;
import com.zhoushuo.eaqb.user.dto.resp.FindUserByPhoneRspDTO;
import feign.RetryableException;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserRpcService {
    private static final String USER_SERVICE_USER_NOT_FOUND_ERROR_CODE = "USER-20008";
    private static final String USER_SERVICE_EMPTY_RESPONSE_ERROR_CODE = "AUTH-RPC-500";
    private static final int REGISTER_RETRY_MAX_ATTEMPTS = 3;
    private static final long REGISTER_RETRY_INTERVAL_MILLIS = 100L;

    @Resource
    private UserFeignApi userFeignApi;

    /**
     * 用户注册
     *
     * @param phone
     * @return
     */
    public Long registerUser(String phone) {
        RegisterUserReqDTO registerUserReqDTO = new RegisterUserReqDTO();
        registerUserReqDTO.setPhone(phone);

        for (int attempt = 1; attempt <= REGISTER_RETRY_MAX_ATTEMPTS; attempt++) {
            try {
                Response<Long> response = userFeignApi.registerUser(registerUserReqDTO);

                if (response == null) {
                    throw new BizException(USER_SERVICE_EMPTY_RESPONSE_ERROR_CODE, "用户服务响应为空");
                }

                if (!response.isSuccess()) {
                    throw new BizException(response.getErrorCode(), response.getMessage());
                }

                return response.getData();
            } catch (RetryableException ex) {
                if (attempt == REGISTER_RETRY_MAX_ATTEMPTS) {
                    log.error("==> 调用用户服务注册接口失败，已重试 {} 次, phone: {}",
                            REGISTER_RETRY_MAX_ATTEMPTS, phone, ex);
                    throw new BizException(USER_SERVICE_EMPTY_RESPONSE_ERROR_CODE, "用户服务暂时不可用，请稍后重试");
                }

                log.warn("==> 调用用户服务注册接口失败，准备重试，phone: {}, attempt: {}", phone, attempt, ex);
                sleepBeforeRetry();
            }
        }

        throw new BizException(USER_SERVICE_EMPTY_RESPONSE_ERROR_CODE, "用户服务重试后仍未返回有效响应");
    }

    /**
     * 根据手机号查询用户信息
     *
     * @param phone
     * @return
     */
    public FindUserByPhoneRspDTO findUserByPhone(String phone) {
        FindUserByPhoneReqDTO findUserByPhoneReqDTO = new FindUserByPhoneReqDTO();
        findUserByPhoneReqDTO.setPhone(phone);

        Response<FindUserByPhoneRspDTO> response = userFeignApi.findByPhone(findUserByPhoneReqDTO);

        if (response == null) {
            throw new BizException("AUTH-RPC-500", "用户服务响应为空");
        }

        if (!response.isSuccess()) {
            if (USER_SERVICE_USER_NOT_FOUND_ERROR_CODE.equals(response.getErrorCode())) {
                return null;
            }
            throw new BizException(response.getErrorCode(), response.getMessage());
        }

        return response.getData();
    }

    /**
     * 密码更新
     *
     * @param password 明文新密码，交由用户服务内部加密后存储
     */
    public void updatePassword(String password) {
        UpdateUserPasswordReqDTO updateUserPasswordReqDTO = new UpdateUserPasswordReqDTO();
        updateUserPasswordReqDTO.setPassword(password);

        userFeignApi.updatePassword(updateUserPasswordReqDTO);
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(REGISTER_RETRY_INTERVAL_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(USER_SERVICE_EMPTY_RESPONSE_ERROR_CODE, "注册重试被中断");
        }
    }

}
