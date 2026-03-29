package com.zhoushuo.eaqb.user.biz.service.impl;

import com.zhoushuo.eaqb.user.biz.constant.RedisKeyConstants;
import com.zhoushuo.eaqb.user.biz.constant.RoleConstants;
import com.zhoushuo.eaqb.user.biz.domain.dataobject.RoleDO;
import com.zhoushuo.eaqb.user.biz.domain.dataobject.UserDO;
import com.zhoushuo.eaqb.user.biz.domain.mapper.RoleDOMapper;
import com.zhoushuo.eaqb.user.biz.domain.mapper.UserDOMapper;
import com.zhoushuo.eaqb.user.biz.domain.mapper.UserRoleDOMapper;
import com.zhoushuo.eaqb.user.biz.rpc.DistributedIdGeneratorRpcService;
import com.zhoushuo.eaqb.user.biz.rpc.OssRpcService;
import com.zhoushuo.eaqb.user.dto.req.FindUserByPhoneReqDTO;
import com.zhoushuo.eaqb.user.dto.req.RegisterUserReqDTO;
import com.zhoushuo.eaqb.user.dto.req.UpdateUserPasswordReqDTO;
import com.zhoushuo.eaqb.user.dto.resp.AdminUserListRspDTO;
import com.zhoushuo.eaqb.user.dto.resp.CurrentUserCredentialRspDTO;
import com.zhoushuo.eaqb.user.biz.model.vo.UpdateUserInfoReqVO;
import com.zhoushuo.eaqb.user.biz.enums.ResponseCodeEnum;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.eumns.DeletedEnum;
import com.zhoushuo.framework.commono.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserDOMapper userDOMapper;
    @Mock
    private OssRpcService ossRpcService;
    @Mock
    private UserRoleDOMapper userRoleDOMapper;
    @Mock
    private RoleDOMapper roleDOMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void register_existingUser_shouldReturnExistingId() {
        RegisterUserReqDTO request = new RegisterUserReqDTO();
        request.setPhone("13800138000");
        UserDO existingUser = new UserDO();
        existingUser.setId(1001L);

        when(userDOMapper.selectByPhone("13800138000")).thenReturn(existingUser);

        Response<Long> response = userService.register(request);

        assertEquals(1001L, response.getData());
        verify(userDOMapper, never()).insert(any());
    }

    @Test
    void register_deletedUser_shouldCreateNewUserInsteadOfReusingDeletedId() {
        RegisterUserReqDTO request = new RegisterUserReqDTO();
        request.setPhone("13800138003");
        RoleDO roleDO = new RoleDO();
        roleDO.setRoleKey("common_user");
        UserDO deletedUser = UserDO.builder()
                .id(3999L)
                .isDeleted(DeletedEnum.YES.getValue())
                .build();

        when(userDOMapper.selectByPhone("13800138003")).thenReturn(deletedUser);
        when(distributedIdGeneratorRpcService.getEaqbId()).thenReturn("10003");
        when(distributedIdGeneratorRpcService.getUserId()).thenReturn("4004");
        when(roleDOMapper.selectByPrimaryKey(RoleConstants.COMMON_USER_ROLE_ID)).thenReturn(roleDO);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        Response<Long> response = userService.register(request);

        assertEquals(4004L, response.getData());
        verify(userDOMapper).insert(any(UserDO.class));
    }

    @Test
    void register_duplicateKey_shouldFallbackToExistingUser() {
        RegisterUserReqDTO request = new RegisterUserReqDTO();
        request.setPhone("13800138001");
        UserDO concurrentCreatedUser = new UserDO();
        concurrentCreatedUser.setId(2002L);

        when(userDOMapper.selectByPhone("13800138001"))
                .thenReturn(null)
                .thenReturn(concurrentCreatedUser);
        when(distributedIdGeneratorRpcService.getEaqbId()).thenReturn("10000");
        when(distributedIdGeneratorRpcService.getUserId()).thenReturn("2002");
        when(userDOMapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate phone"));

        Response<Long> response = userService.register(request);

        assertEquals(2002L, response.getData());
        verify(userRoleDOMapper, never()).insert(any());
    }

    @Test
    void register_newUser_shouldPersistEmptyPassword() {
        RegisterUserReqDTO request = new RegisterUserReqDTO();
        request.setPhone("13800138002");
        RoleDO roleDO = new RoleDO();
        roleDO.setRoleKey("common_user");

        when(userDOMapper.selectByPhone("13800138002")).thenReturn(null);
        when(distributedIdGeneratorRpcService.getEaqbId()).thenReturn("10001");
        when(distributedIdGeneratorRpcService.getUserId()).thenReturn("3003");
        when(roleDOMapper.selectByPrimaryKey(RoleConstants.COMMON_USER_ROLE_ID)).thenReturn(roleDO);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        Response<Long> response = userService.register(request);

        ArgumentCaptor<UserDO> userCaptor = ArgumentCaptor.forClass(UserDO.class);
        verify(userDOMapper).insert(userCaptor.capture());
        verify(valueOperations, times(1)).set(eq(RedisKeyConstants.buildUserRoleKey(3003L)), any());
        assertEquals("", userCaptor.getValue().getPassword());
        assertEquals(3003L, response.getData());
    }

    @Test
    void getCurrentUserCredential_shouldReturnPhone() {
        LoginUserContextHolder.setUserId(3003L);
        try {
            UserDO userDO = UserDO.builder()
                    .id(3003L)
                    .phone("13800138002")
                    .build();
            when(userDOMapper.selectByPrimaryKey(3003L)).thenReturn(userDO);

            Response<CurrentUserCredentialRspDTO> response = userService.getCurrentUserCredential();

            assertEquals("13800138002", response.getData().getPhone());
        } finally {
            LoginUserContextHolder.remove();
        }
    }

    @Test
    void findByPhone_deletedUser_shouldThrowUserNotFound() {
        FindUserByPhoneReqDTO request = new FindUserByPhoneReqDTO();
        request.setPhone("13800138004");
        when(userDOMapper.selectByPhone("13800138004")).thenReturn(
                UserDO.builder()
                        .id(5005L)
                        .password("hashed")
                        .isDeleted(DeletedEnum.YES.getValue())
                        .build()
        );

        BizException ex = assertThrows(BizException.class, () -> userService.findByPhone(request));

        assertEquals(ResponseCodeEnum.USER_NOT_FOUND.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void getCurrentUserCredential_deletedUser_shouldThrowUserNotFound() {
        LoginUserContextHolder.setUserId(5006L);
        try {
            when(userDOMapper.selectByPrimaryKey(5006L)).thenReturn(
                    UserDO.builder()
                            .id(5006L)
                            .phone("13800138005")
                            .isDeleted(DeletedEnum.YES.getValue())
                            .build()
            );

            BizException ex = assertThrows(BizException.class, () -> userService.getCurrentUserCredential());

            assertEquals(ResponseCodeEnum.USER_NOT_FOUND.getErrorCode(), ex.getErrorCode());
        } finally {
            LoginUserContextHolder.remove();
        }
    }

    @Test
    void updatePassword_deletedUser_shouldThrowUserNotFound() {
        LoginUserContextHolder.setUserId(5007L);
        try {
            when(userDOMapper.selectByPrimaryKey(5007L)).thenReturn(
                    UserDO.builder()
                            .id(5007L)
                            .isDeleted(DeletedEnum.YES.getValue())
                            .build()
            );
            UpdateUserPasswordReqDTO request = new UpdateUserPasswordReqDTO();
            request.setPassword("new-password");

            BizException ex = assertThrows(BizException.class, () -> userService.updatePassword(request));

            assertEquals(ResponseCodeEnum.USER_NOT_FOUND.getErrorCode(), ex.getErrorCode());
            verify(passwordEncoder, never()).encode(any());
            verify(userDOMapper, never()).updatePasswordByIdIfActive(eq(5007L), any(), any());
        } finally {
            LoginUserContextHolder.remove();
        }
    }

    @Test
    void updatePassword_updateRowsZero_shouldThrowPasswordUpdateFailed() {
        LoginUserContextHolder.setUserId(5008L);
        try {
            when(userDOMapper.selectByPrimaryKey(5008L)).thenReturn(
                    UserDO.builder()
                            .id(5008L)
                            .isDeleted(DeletedEnum.NO.getValue())
                            .build()
            );
            when(passwordEncoder.encode("new-password")).thenReturn("encoded-password");
            when(userDOMapper.updatePasswordByIdIfActive(eq(5008L), eq("encoded-password"), any())).thenReturn(0);
            UpdateUserPasswordReqDTO request = new UpdateUserPasswordReqDTO();
            request.setPassword("new-password");

            BizException ex = assertThrows(BizException.class, () -> userService.updatePassword(request));

            assertEquals(ResponseCodeEnum.PASSWORD_UPDATE_FAILED.getErrorCode(), ex.getErrorCode());
        } finally {
            LoginUserContextHolder.remove();
        }
    }

    @Test
    void listUsersForAdmin_shouldReturnGlobalUsers() {
        UserDO user = UserDO.builder()
                .id(1L)
                .eaqbId("10001")
                .nickname("题库系统10001")
                .phone("13800138000")
                .status(0)
                .build();
        when(userDOMapper.selectAdminUserList()).thenReturn(java.util.List.of(user));

        Response<java.util.List<AdminUserListRspDTO>> response = userService.listUsersForAdmin();

        assertEquals(1, response.getData().size());
        assertEquals(1L, response.getData().get(0).getId());
        assertEquals("10001", response.getData().get(0).getEaqbId());
    }

    @Test
    void listUsersForAdmin_whenNoUser_shouldReturnEmptyList() {
        when(userDOMapper.selectAdminUserList()).thenReturn(java.util.Collections.emptyList());

        Response<java.util.List<AdminUserListRspDTO>> response = userService.listUsersForAdmin();

        assertTrue(response.getData().isEmpty());
    }

    @Test
    void updateUserInfo_validAvatar_shouldCallAvatarUploadAndPersistUrl() {
        LoginUserContextHolder.setUserId(6001L);
        try {
            MockMultipartFile avatar = new MockMultipartFile(
                    "avatar",
                    "avatar.png",
                    "image/png",
                    new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}
            );
            UpdateUserInfoReqVO request = UpdateUserInfoReqVO.builder()
                    .avatar(avatar)
                    .build();
            when(ossRpcService.uploadAvatar(avatar)).thenReturn("https://oss/avatar.png");

            Response<?> response = userService.updateUserInfo(request);

            assertTrue(response.isSuccess());
            ArgumentCaptor<UserDO> userCaptor = ArgumentCaptor.forClass(UserDO.class);
            verify(ossRpcService).uploadAvatar(avatar);
            verify(ossRpcService, never()).uploadBackground(any());
            verify(userDOMapper).updateByPrimaryKeySelective(userCaptor.capture());
            assertEquals(6001L, userCaptor.getValue().getId());
            assertEquals("https://oss/avatar.png", userCaptor.getValue().getAvatar());
        } finally {
            LoginUserContextHolder.remove();
        }
    }

    @Test
    void updateUserInfo_validBackground_shouldCallBackgroundUploadAndPersistUrl() {
        LoginUserContextHolder.setUserId(6002L);
        try {
            MockMultipartFile background = new MockMultipartFile(
                    "backgroundImg",
                    "background.jpg",
                    "image/jpeg",
                    new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}
            );
            UpdateUserInfoReqVO request = UpdateUserInfoReqVO.builder()
                    .backgroundImg(background)
                    .build();
            when(ossRpcService.uploadBackground(background)).thenReturn("https://oss/background.jpg");

            Response<?> response = userService.updateUserInfo(request);

            assertTrue(response.isSuccess());
            ArgumentCaptor<UserDO> userCaptor = ArgumentCaptor.forClass(UserDO.class);
            verify(ossRpcService).uploadBackground(background);
            verify(ossRpcService, never()).uploadAvatar(any());
            verify(userDOMapper).updateByPrimaryKeySelective(userCaptor.capture());
            assertEquals(6002L, userCaptor.getValue().getId());
            assertEquals("https://oss/background.jpg", userCaptor.getValue().getBackgroundImg());
        } finally {
            LoginUserContextHolder.remove();
        }
    }

    @Test
    void updateUserInfo_invalidAvatar_shouldRejectBeforeOssUpload() {
        LoginUserContextHolder.setUserId(6003L);
        try {
            MockMultipartFile avatar = new MockMultipartFile(
                    "avatar",
                    "avatar.txt",
                    "text/plain",
                    "not-image".getBytes()
            );
            UpdateUserInfoReqVO request = UpdateUserInfoReqVO.builder()
                    .avatar(avatar)
                    .build();

            BizException ex = assertThrows(BizException.class, () -> userService.updateUserInfo(request));

            assertEquals(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), ex.getErrorCode());
            verify(ossRpcService, never()).uploadAvatar(any());
            verify(userDOMapper, never()).updateByPrimaryKeySelective(any());
        } finally {
            LoginUserContextHolder.remove();
        }
    }
}
