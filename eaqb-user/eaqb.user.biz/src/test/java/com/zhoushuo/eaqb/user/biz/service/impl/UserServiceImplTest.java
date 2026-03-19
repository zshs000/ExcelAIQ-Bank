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
import com.zhoushuo.eaqb.user.dto.req.RegisterUserReqDTO;
import com.zhoushuo.framework.commono.response.Response;
import com.zhoushuo.eaqb.oss.api.FileFeignApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private FileFeignApi fileFeignApi;
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
        when(passwordEncoder.encode("123456")).thenReturn("encoded-default-password");
        when(userDOMapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate phone"));

        Response<Long> response = userService.register(request);

        assertEquals(2002L, response.getData());
        verify(userRoleDOMapper, never()).insert(any());
    }

    @Test
    void register_newUser_shouldPersistEncodedDefaultPassword() {
        RegisterUserReqDTO request = new RegisterUserReqDTO();
        request.setPhone("13800138002");
        RoleDO roleDO = new RoleDO();
        roleDO.setRoleKey("common_user");

        when(userDOMapper.selectByPhone("13800138002")).thenReturn(null);
        when(distributedIdGeneratorRpcService.getEaqbId()).thenReturn("10001");
        when(distributedIdGeneratorRpcService.getUserId()).thenReturn("3003");
        when(passwordEncoder.encode("123456")).thenReturn("encoded-default-password");
        when(roleDOMapper.selectByPrimaryKey(RoleConstants.COMMON_USER_ROLE_ID)).thenReturn(roleDO);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        Response<Long> response = userService.register(request);

        ArgumentCaptor<UserDO> userCaptor = ArgumentCaptor.forClass(UserDO.class);
        verify(userDOMapper).insert(userCaptor.capture());
        verify(valueOperations, times(1)).set(eq(RedisKeyConstants.buildUserRoleKey(3003L)), any());
        assertEquals("encoded-default-password", userCaptor.getValue().getPassword());
        assertEquals(3003L, response.getData());
    }
}
