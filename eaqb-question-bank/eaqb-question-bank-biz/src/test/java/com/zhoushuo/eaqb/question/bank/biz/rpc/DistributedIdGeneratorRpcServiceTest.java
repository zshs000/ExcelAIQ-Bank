package com.zhoushuo.eaqb.question.bank.biz.rpc;

import com.zhoushuo.eaqb.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import com.zhoushuo.framework.common.exception.BizException;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

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
    void nextQuestionBankEntityId_emptyResponse_shouldThrowBizException() {
        when(distributedIdGeneratorFeignApi.getSegmentId("leaf-segment-questionbank-id")).thenReturn(null);

        BizException exception = assertThrows(BizException.class,
                () -> distributedIdGeneratorRpcService.nextQuestionBankEntityId());

        assertEquals("QUESTION-20008", exception.getErrorCode());
        assertEquals("分布式ID服务响应为空", exception.getErrorMessage());
    }

    @Test
    void nextQuestionBankEntityId_blankResponse_shouldThrowBizException() {
        when(distributedIdGeneratorFeignApi.getSegmentId("leaf-segment-questionbank-id")).thenReturn("   ");

        BizException exception = assertThrows(BizException.class,
                () -> distributedIdGeneratorRpcService.nextQuestionBankEntityId());

        assertEquals("QUESTION-20008", exception.getErrorCode());
        assertEquals("分布式ID服务返回了空白ID", exception.getErrorMessage());
    }

    @Test
    void nextQuestionBankEntityId_nonNumericResponse_shouldThrowBizException() {
        when(distributedIdGeneratorFeignApi.getSegmentId("leaf-segment-questionbank-id")).thenReturn("abc");

        BizException exception = assertThrows(BizException.class,
                () -> distributedIdGeneratorRpcService.nextQuestionBankEntityId());

        assertEquals("QUESTION-20008", exception.getErrorCode());
        assertEquals("分布式ID服务返回的ID非法: abc", exception.getErrorMessage());
    }

    @Test
    void nextQuestionBankEntityId_retryableException_shouldThrowBizException() {
        RetryableException retryableException = new RetryableException(
                503,
                "temporary network error",
                Request.HttpMethod.GET,
                new Date(),
                Request.create(Request.HttpMethod.GET, "/id/segment/get/leaf-segment-questionbank-id",
                        Collections.emptyMap(), null, StandardCharsets.UTF_8)
        );
        when(distributedIdGeneratorFeignApi.getSegmentId("leaf-segment-questionbank-id"))
                .thenThrow(retryableException);

        BizException exception = assertThrows(BizException.class,
                () -> distributedIdGeneratorRpcService.nextQuestionBankEntityId());

        assertEquals("QUESTION-20008", exception.getErrorCode());
        assertEquals("分布式ID服务调用失败", exception.getErrorMessage());
    }
    @Test
    void nextQuestionBankEntityIds_validBatchResponse_shouldReturnIds() {
        when(distributedIdGeneratorFeignApi.getSegmentIds("leaf-segment-questionbank-id", 3))
                .thenReturn(Arrays.asList("101", "102", "103"));

        List<Long> ids = distributedIdGeneratorRpcService.nextQuestionBankEntityIds(3);

        assertEquals(Arrays.asList(101L, 102L, 103L), ids);
    }

    @Test
    void nextQuestionBankEntityIds_sizeMismatch_shouldThrowBizException() {
        when(distributedIdGeneratorFeignApi.getSegmentIds("leaf-segment-questionbank-id", 3))
                .thenReturn(Arrays.asList("101", "102"));

        BizException exception = assertThrows(BizException.class,
                () -> distributedIdGeneratorRpcService.nextQuestionBankEntityIds(3));

        assertEquals("QUESTION-20008", exception.getErrorCode());
    }
}
