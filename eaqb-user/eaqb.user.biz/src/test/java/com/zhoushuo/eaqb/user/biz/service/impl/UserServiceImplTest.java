package com.zhoushuo.eaqb.user.biz.service.impl;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        when(userDOMapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate phone"));

        Response<Long> response = userService.register(request);

        assertEquals(2002L, response.getData());
        verify(userRoleDOMapper, never()).insert(any());
    }
}
