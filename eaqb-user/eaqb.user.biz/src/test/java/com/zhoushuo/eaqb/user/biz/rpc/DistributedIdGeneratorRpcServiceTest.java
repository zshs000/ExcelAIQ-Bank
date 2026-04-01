package com.zhoushuo.eaqb.user.biz.rpc;

import com.zhoushuo.eaqb.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import com.zhoushuo.framework.commono.exception.BizException;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistributedIdGeneratorRpcServiceTest {

    @Mock
    private DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;

    @InjectMocks
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;

    @Test
    void getUserId_emptyResponse_shouldThrowBizException() {
        when(distributedIdGeneratorFeignApi.getSegmentId("leaf-segment-user-id")).thenReturn(null);

        BizException exception = assertThrows(BizException.class,
                () -> distributedIdGeneratorRpcService.getUserId());

        assertEquals("USER-20010", exception.getErrorCode());
        assertEquals("分布式ID服务响应为空", exception.getErrorMessage());
    }

    @Test
    void getEaqbId_blankResponse_shouldThrowBizException() {
        when(distributedIdGeneratorFeignApi.getSegmentId("leaf-segment-eaqb-id")).thenReturn(" ");

        BizException exception = assertThrows(BizException.class,
                () -> distributedIdGeneratorRpcService.getEaqbId());

        assertEquals("USER-20010", exception.getErrorCode());
        assertEquals("分布式ID服务返回了空白ID", exception.getErrorMessage());
    }

    @Test
    void getUserId_nonNumericResponse_shouldThrowBizException() {
        when(distributedIdGeneratorFeignApi.getSegmentId("leaf-segment-user-id")).thenReturn("user-id");

        BizException exception = assertThrows(BizException.class,
                () -> distributedIdGeneratorRpcService.getUserId());

        assertEquals("USER-20010", exception.getErrorCode());
        assertEquals("分布式ID服务返回的ID非法: user-id", exception.getErrorMessage());
    }

    @Test
    void getUserId_retryableException_shouldThrowBizException() {
        RetryableException retryableException = new RetryableException(
                503,
                "temporary network error",
                Request.HttpMethod.GET,
                new Date(),
                Request.create(Request.HttpMethod.GET, "/id/segment/get/leaf-segment-user-id",
                        Collections.emptyMap(), null, StandardCharsets.UTF_8)
        );
        when(distributedIdGeneratorFeignApi.getSegmentId("leaf-segment-user-id"))
                .thenThrow(retryableException);

        BizException exception = assertThrows(BizException.class,
                () -> distributedIdGeneratorRpcService.getUserId());

        assertEquals("USER-20010", exception.getErrorCode());
        assertEquals("分布式ID服务调用失败", exception.getErrorMessage());
    }
}
