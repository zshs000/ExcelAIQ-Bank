package com.zhoushuo.eaqb.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.google.common.base.Preconditions;
import com.zhoushuo.eaqb.auth.modle.vo.user.UpdatePasswordReqVO;
import com.zhoushuo.eaqb.auth.rpc.UserRpcService;
import com.zhoushuo.eaqb.user.dto.resp.FindUserByPhoneRspDTO;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.eaqb.auth.constant.RedisKeyConstants;

import com.zhoushuo.eaqb.auth.enums.LoginTypeEnum;
import com.zhoushuo.eaqb.auth.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.auth.modle.vo.user.UserLoginReqVO;
import com.zhoushuo.eaqb.auth.service.AuthService;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;


@Service
@Slf4j
public class AuthServiceImpl implements AuthService {


    @Resource
    private RedisTemplate<String, Object> redisTemplate;






    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private UserRpcService userRpcService;

    /**
     * 登录与注册
     *
     * @param userLoginReqVO
     * @return
     */
    @Override
    public Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO) {
        String phone = userLoginReqVO.getPhone();
        Integer type = userLoginReqVO.getType();

        LoginTypeEnum loginTypeEnum = LoginTypeEnum.valueOf(type);

        Long userId = null;

        // 判断登录类型
        switch (loginTypeEnum) {
            case VERIFICATION_CODE: // 验证码登录
                String verificationCode = userLoginReqVO.getCode();

                // 校验入参验证码是否为空

                Preconditions.checkArgument(StringUtils.isNotBlank(verificationCode), "验证码不能为空");

                // 构建验证码 Redis Key
                String key = RedisKeyConstants.buildVerificationCodeKey(phone);
                // 查询存储在 Redis 中该用户的登录验证码
                String sentCode = (String) redisTemplate.opsForValue().get(key);

                // 判断用户提交的验证码，与 Redis 中的验证码是否一致
                if (!StringUtils.equals(verificationCode, sentCode)) {
                    throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
                }

                // RPC: 调用用户服务，注册用户
                Long userIdTmp = userRpcService.registerUser(phone);

                // 若调用用户服务，返回的用户 ID 为空，则提示登录失败
                if (Objects.isNull(userIdTmp)) {
                    throw new BizException(ResponseCodeEnum.LOGIN_FAIL);
                }

                userId = userIdTmp;
                break;
            case PASSWORD: // 密码登录
                String password = userLoginReqVO.getPassword();

                // RPC: 调用用户服务，通过手机号查询用户
                FindUserByPhoneRspDTO findUserByPhoneRspDTO = userRpcService.findUserByPhone(phone);

                // 判断该手机号是否注册
                if (Objects.isNull(findUserByPhoneRspDTO)) {
                    throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
                }

                // 拿到密文密码
                String encodePassword = findUserByPhoneRspDTO.getPassword();

                // 匹配密码是否一致
                boolean isPasswordCorrect = passwordEncoder.matches(password, encodePassword);

                // 如果不正确，则抛出业务异常，提示用户名或者密码不正确
                if (!isPasswordCorrect) {
                    throw new BizException(ResponseCodeEnum.PHONE_OR_PASSWORD_ERROR);
                }

                userId = findUserByPhoneRspDTO.getId();
                break;


            default:
                break;
        }

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
//    /**
//     * 系统自动注册用户
//     * @param phone
//     * @return
//     */
//    @Transactional(rollbackFor = Exception.class)
//    public Long registerUser(String phone) {
//        return transactionTemplate.execute(status -> {
//
//            try {
//                // 获取全局自增的 ID
//                Long eaqbId = redisTemplate.opsForValue().increment(RedisKeyConstants.EAQB_ID_GENERATOR_KEY);
//
//                UserDO userDO = UserDO.builder()
//                        .phone(phone)
//                        .eaqbId(String.valueOf(eaqbId)) // 自动生成小红书号 ID
//                        .nickname("题库系统" + eaqbId) // 自动生成昵称, 如：题库系统10000
//                        .status(StatusEnum.ENABLE.getValue()) // 状态为启用
//                        .createTime(LocalDateTime.now())
//                        .updateTime(LocalDateTime.now())
//                        .isDeleted(DeletedEnum.NO.getValue()) // 逻辑删除
//                        .build();
//
//                // 添加入库
//                userDOMapper.insert(userDO);
//
//                // 获取刚刚添加入库的用户 ID
//                Long userId = userDO.getId();
//
//                // 给该用户分配一个默认角色
//                UserRoleDO userRoleDO = UserRoleDO.builder()
//                        .userId(userId)
//                        .roleId(RoleConstants.COMMON_USER_ROLE_ID)
//                        .createTime(LocalDateTime.now())
//                        .updateTime(LocalDateTime.now())
//                        .isDeleted(DeletedEnum.NO.getValue())
//                        .build();
//                userRoleDOMapper.insert(userRoleDO);
//
//
//
//                RoleDO roleDO = roleDOMapper.selectByPrimaryKey(RoleConstants.COMMON_USER_ROLE_ID);
//
//                // 将该用户的角色 ID 存入 Redis 中，指定初始容量为 1，这样可以减少在扩容时的性能开销
//                List<String> roles = new ArrayList<>(1);
//                roles.add(roleDO.getRoleKey());
//
//                String userRolesKey = RedisKeyConstants.buildUserRoleKey(userId);
//                redisTemplate.opsForValue().set(userRolesKey, JsonUtils.toJsonString(roles));
//
//                return userId;
//
//            } catch (Exception e) {
//                status.setRollbackOnly(); // 标记事务为回滚
//                log.error("==> 系统注册用户异常: ", e);
//                return null;
//            }
//
//
//        });
//
//    }

}