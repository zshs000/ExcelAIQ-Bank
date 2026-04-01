package com.zhoushuo.eaqb.excel.parser.biz.rpc;

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
    void getFileId_emptyResponse_shouldThrowBizException() {
        when(distributedIdGeneratorFeignApi.getSegmentId("leaf-segment-file-id")).thenReturn(null);

        BizException exception = assertThrows(BizException.class,
                () -> distributedIdGeneratorRpcService.getFileId());

        assertEquals("EXCEL-20010", exception.getErrorCode());
        assertEquals("分布式ID服务响应为空", exception.getErrorMessage());
    }

    @Test
    void getPreFileId_blankResponse_shouldThrowBizException() {
        when(distributedIdGeneratorFeignApi.getSegmentId("leaf-segment-pre-file-id")).thenReturn(" ");

        BizException exception = assertThrows(BizException.class,
                () -> distributedIdGeneratorRpcService.getPreFileId());

        assertEquals("EXCEL-20010", exception.getErrorCode());
        assertEquals("分布式ID服务返回了空白ID", exception.getErrorMessage());
    }

    @Test
    void getFileId_nonNumericResponse_shouldThrowBizException() {
        when(distributedIdGeneratorFeignApi.getSegmentId("leaf-segment-file-id")).thenReturn("file-id");

        BizException exception = assertThrows(BizException.class,
                () -> distributedIdGeneratorRpcService.getFileId());

        assertEquals("EXCEL-20010", exception.getErrorCode());
        assertEquals("分布式ID服务返回的ID非法: file-id", exception.getErrorMessage());
    }

    @Test
    void getFileId_retryableException_shouldThrowBizException() {
        RetryableException retryableException = new RetryableException(
                503,
                "temporary network error",
                Request.HttpMethod.GET,
                new Date(),
                Request.create(Request.HttpMethod.GET, "/id/segment/get/leaf-segment-file-id",
                        Collections.emptyMap(), null, StandardCharsets.UTF_8)
        );
        when(distributedIdGeneratorFeignApi.getSegmentId("leaf-segment-file-id"))
                .thenThrow(retryableException);

        BizException exception = assertThrows(BizException.class,
                () -> distributedIdGeneratorRpcService.getFileId());

        assertEquals("EXCEL-20010", exception.getErrorCode());
        assertEquals("分布式ID服务调用失败", exception.getErrorMessage());
    }
}
