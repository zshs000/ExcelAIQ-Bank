package com.zhoushuo.eaqb.user.biz.service.impl;

import com.alibaba.nacos.shaded.com.google.common.base.Preconditions;
import com.zhoushuo.eaqb.user.biz.rpc.DistributedIdGeneratorRpcService;
import com.zhoushuo.eaqb.user.dto.req.FindUserByPhoneReqDTO;
import com.zhoushuo.eaqb.user.dto.req.RegisterUserReqDTO;
import com.zhoushuo.eaqb.user.biz.constant.RedisKeyConstants;
import com.zhoushuo.eaqb.user.biz.constant.RoleConstants;
import com.zhoushuo.eaqb.user.biz.domain.dataobject.RoleDO;
import com.zhoushuo.eaqb.user.biz.domain.dataobject.UserDO;
import com.zhoushuo.eaqb.user.biz.domain.dataobject.UserRoleDO;
import com.zhoushuo.eaqb.user.biz.domain.mapper.RoleDOMapper;
import com.zhoushuo.eaqb.user.biz.domain.mapper.UserDOMapper;
import com.zhoushuo.eaqb.user.biz.domain.mapper.UserRoleDOMapper;
import com.zhoushuo.eaqb.user.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.user.biz.enums.SexEnum;
import com.zhoushuo.eaqb.user.biz.model.vo.UpdateUserInfoReqVO;
import com.zhoushuo.eaqb.user.biz.rpc.OssRpcService;
import com.zhoushuo.eaqb.user.biz.service.UserService;
import com.zhoushuo.eaqb.user.biz.util.ImageUploadValidator;
import com.zhoushuo.eaqb.user.dto.req.UpdateUserPasswordReqDTO;
import com.zhoushuo.eaqb.user.dto.resp.AdminUserListRspDTO;
import com.zhoushuo.eaqb.user.dto.resp.CurrentUserCredentialRspDTO;
import com.zhoushuo.eaqb.user.dto.resp.FindUserByPhoneRspDTO;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.eumns.DeletedEnum;
import com.zhoushuo.framework.commono.eumns.StatusEnum;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;
import com.zhoushuo.framework.commono.util.JsonUtils;
import com.zhoushuo.framework.commono.util.ParamUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
    @Resource
    private UserDOMapper userDOMapper;
    @Resource
    private OssRpcService ossRpcService;

    @Resource
    private UserRoleDOMapper userRoleDOMapper;
    @Resource
    private RoleDOMapper roleDOMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;

    @Resource
    private PasswordEncoder passwordEncoder;

    /**
     * 更新用户信息
     *
     * @param updateUserInfoReqVO
     * @return
     */
    @Override
    public Response<?> updateUserInfo(UpdateUserInfoReqVO updateUserInfoReqVO) {
        UserDO userDO = new UserDO();
        // 设置当前需要更新的用户 ID
        userDO.setId(LoginUserContextHolder.getUserId());
        // 标识位：是否需要更新
        boolean needUpdate = false;

        // 头像
        MultipartFile avatarFile = updateUserInfoReqVO.getAvatar();

        if (Objects.nonNull(avatarFile)) {
            ImageUploadValidator.validate(avatarFile);
            String avatar = ossRpcService.uploadAvatar(avatarFile);
            log.info("==> 调用 oss 服务成功，上传头像，url：{}", avatar);

            // 若上传头像失败，则抛出业务异常
            if (StringUtils.isBlank(avatar)) {
                throw new BizException(ResponseCodeEnum.UPLOAD_AVATAR_FAIL);
            }

            userDO.setAvatar(avatar);
            needUpdate = true;
        }

        // 昵称
        String nickname = updateUserInfoReqVO.getNickname();
        if (StringUtils.isNotBlank(nickname)) {
            Preconditions.checkArgument(ParamUtils.checkNickname(nickname), ResponseCodeEnum.NICK_NAME_VALID_FAIL.getErrorMessage());
            userDO.setNickname(nickname);
            needUpdate = true;
        }

        // 题库系统号
        String eaqbId = updateUserInfoReqVO.getEaqbId();
        if (StringUtils.isNotBlank(eaqbId)) {
            Preconditions.checkArgument(ParamUtils.checkEaqbId(eaqbId), ResponseCodeEnum.EAQB_ID_VALID_FAIL.getErrorMessage());
            userDO.setEaqbId(eaqbId);
            needUpdate = true;
        }

        // 性别
        Integer sex = updateUserInfoReqVO.getSex();
        if (Objects.nonNull(sex)) {
            Preconditions.checkArgument(SexEnum.isValid(sex), ResponseCodeEnum.SEX_VALID_FAIL.getErrorMessage());
            userDO.setSex(sex);
            needUpdate = true;
        }

        // 生日
        LocalDate birthday = updateUserInfoReqVO.getBirthday();
        if (Objects.nonNull(birthday)) {
            userDO.setBirthday(birthday);
            needUpdate = true;
        }

        // 个人简介
        String introduction = updateUserInfoReqVO.getIntroduction();
        if (StringUtils.isNotBlank(introduction)) {
            Preconditions.checkArgument(ParamUtils.checkLength(introduction, 100), ResponseCodeEnum.INTRODUCTION_VALID_FAIL.getErrorMessage());
            userDO.setIntroduction(introduction);
            needUpdate = true;
        }

        // 背景图
        MultipartFile backgroundImgFile = updateUserInfoReqVO.getBackgroundImg();
        if (Objects.nonNull(backgroundImgFile)) {
            ImageUploadValidator.validate(backgroundImgFile);
            String backgroundImg = ossRpcService.uploadBackground(backgroundImgFile);
            log.info("==> 调用 oss 服务成功，上传背景图，url：{}", backgroundImg);

            // 若上传背景图失败，则抛出业务异常
            if (StringUtils.isBlank(backgroundImg)) {
                throw new BizException(ResponseCodeEnum.UPLOAD_BACKGROUND_IMG_FAIL);
            }

            userDO.setBackgroundImg(backgroundImg);
            needUpdate = true;
        }

        if (needUpdate) {
            // 更新用户信息
            userDO.setUpdateTime(LocalDateTime.now());
            userDOMapper.updateByPrimaryKeySelective(userDO);
        }
        return Response.success();
    }

    /**
     * 用户注册
     *
     * @param registerUserReqDTO
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<Long> register(RegisterUserReqDTO registerUserReqDTO) {
        String phone = registerUserReqDTO.getPhone();

        // 先判断该手机号是否已被注册
        UserDO existingUser = activeUserOrNull(userDOMapper.selectByPhone(phone));

        log.info("==> 用户是否注册, phone: {}, userDO: {}", phone, JsonUtils.toJsonString(existingUser));

        // 验证码登录走“注册/登录合一”语义：
        // 老用户不重复创建，直接返回已有 userId；新用户才继续执行创建流程。
        if (Objects.nonNull(existingUser)) {
            return Response.success(existingUser.getId());
        }

        try {
            return Response.success(createUser(phone));
        } catch (DuplicateKeyException ex) {
            // 手机号唯一约束是最后一道兜底。若并发下已有请求抢先创建成功，这里回查并复用已有用户。
            log.warn("==> 用户注册命中唯一约束，回查已有用户, phone: {}", phone, ex);
            UserDO concurrentCreatedUser = activeUserOrNull(userDOMapper.selectByPhone(phone));
            if (Objects.nonNull(concurrentCreatedUser)) {
                return Response.success(concurrentCreatedUser.getId());
            }
            throw ex;
        }
    }

    /**
     * 根据手机号查询用户信息
     *
     * @param  findUserByPhoneReqDTO
     * @return
     */
    @Override
    public Response<FindUserByPhoneRspDTO> findByPhone(FindUserByPhoneReqDTO findUserByPhoneReqDTO) {
        String phone = findUserByPhoneReqDTO.getPhone();

        // 根据手机号查询用户信息
        UserDO userDO = activeUserOrNull(userDOMapper.selectByPhone(phone));

        // 判空
        if (Objects.isNull(userDO)) {
            throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
        }


        // 构建返参
        FindUserByPhoneRspDTO findUserByPhoneRspDTO = FindUserByPhoneRspDTO.builder()
                .id(userDO.getId())
                .passwordHash(userDO.getPassword())
                .build();

        return Response.success(findUserByPhoneRspDTO);
    }

    @Override
    public Response<CurrentUserCredentialRspDTO> getCurrentUserCredential() {
        Long userId = LoginUserContextHolder.getUserId();
        UserDO userDO = getActiveUserById(userId);

        CurrentUserCredentialRspDTO currentUserPhoneRspDTO = CurrentUserCredentialRspDTO.builder()
                .id(userDO.getId())
                .phone(userDO.getPhone())
                .build();
        return Response.success(currentUserPhoneRspDTO);
    }

    /**
     * 更新密码
     *
     * @param updateUserPasswordReqDTO
     * @return
     */
    @Override
    public Response<?> updatePassword(UpdateUserPasswordReqDTO updateUserPasswordReqDTO) {
        // 获取当前请求对应的用户 ID
        Long userId = LoginUserContextHolder.getUserId();
        getActiveUserById(userId);
        log.info("修改密码开始，用户：{}", userId);
        String encodePassword = passwordEncoder.encode(updateUserPasswordReqDTO.getPassword());
        int updatedRows = userDOMapper.updatePasswordByIdIfActive(userId, encodePassword, LocalDateTime.now());
        if (updatedRows <= 0) {
            throw new BizException(ResponseCodeEnum.PASSWORD_UPDATE_FAILED);
        }

        return Response.success();
    }

    @Override
    public Response<List<AdminUserListRspDTO>> listUsersForAdmin() {
        List<UserDO> userDOS = userDOMapper.selectAdminUserList();

        List<AdminUserListRspDTO> users = userDOS.stream()
                .map(user -> AdminUserListRspDTO.builder()
                        .id(user.getId())
                        .eaqbId(user.getEaqbId())
                        .nickname(user.getNickname())
                        .phone(user.getPhone())
                        .status(user.getStatus())
                        .createTime(user.getCreateTime())
                        .build())
                .toList();

        return Response.success(users);
    }

    private Long createUser(String phone) {
        // RPC: 调用分布式 ID 生成服务生成题库系统 ID
        String eaqbId = distributedIdGeneratorRpcService.getEaqbId();

        // RPC: 调用分布式 ID 生成服务生成用户 ID
        String userIdStr = distributedIdGeneratorRpcService.getUserId();
        Long userId = Long.valueOf(userIdStr);

        UserDO userDO = UserDO.builder()
                .id(userId)
                .phone(phone)
                .eaqbId(String.valueOf(eaqbId))
                // 验证码登录自动注册的新用户默认不初始化密码，避免产生可枚举的默认口令。
                .password(StringUtils.EMPTY)
                .nickname("题库系统" + eaqbId)
                .status(StatusEnum.ENABLE.getValue())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDeleted(DeletedEnum.NO.getValue())
                .build();
        userDOMapper.insert(userDO);

        UserRoleDO userRoleDO = UserRoleDO.builder()
                .userId(userId)
                .roleId(RoleConstants.COMMON_USER_ROLE_ID)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDeleted(DeletedEnum.NO.getValue())
                .build();
        userRoleDOMapper.insert(userRoleDO);

        RoleDO roleDO = roleDOMapper.selectByPrimaryKey(RoleConstants.COMMON_USER_ROLE_ID);
        List<String> roles = new ArrayList<>(1);
        roles.add(roleDO.getRoleKey());

        String userRolesKey = RedisKeyConstants.buildUserRoleKey(userId);
        redisTemplate.opsForValue().set(userRolesKey, JsonUtils.toJsonString(roles));

        return userId;
    }

    private UserDO getActiveUserById(Long userId) {
        UserDO userDO = userDOMapper.selectByPrimaryKey(userId);
        if (Objects.isNull(userDO) || Boolean.TRUE.equals(userDO.getIsDeleted())) {
            throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
        }
        return userDO;
    }

    private UserDO activeUserOrNull(UserDO userDO) {
        if (Objects.isNull(userDO) || Boolean.TRUE.equals(userDO.getIsDeleted())) {
            return null;
        }
        return userDO;
    }
}
