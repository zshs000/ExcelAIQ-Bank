package com.zhoushuo.eaqb.user.biz.rpc;

import com.zhoushuo.eaqb.oss.api.FileFeignApi;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OssRpcServiceTest {

    @Mock
    private FileFeignApi fileFeignApi;

    @InjectMocks
    private OssRpcService ossRpcService;

    @Test
    void uploadAvatar_shouldThrowBizExceptionWhenResponseIsNull() {
        when(fileFeignApi.uploadAvatar(any())).thenReturn(null);

        BizException exception = assertThrows(BizException.class,
                () -> ossRpcService.uploadAvatar(new MockMultipartFile(
                        "avatar", "avatar.png", "image/png", new byte[]{1})));

        assertEquals("USER-OSS-500", exception.getErrorCode());
        assertEquals("OSS 服务响应为空", exception.getErrorMessage());
    }

    @Test
    void uploadBackground_shouldThrowBizExceptionWhenResponseIsFail() {
        when(fileFeignApi.uploadBackground(any())).thenReturn(Response.fail("OSS-9", "upload background failed"));

        BizException exception = assertThrows(BizException.class,
                () -> ossRpcService.uploadBackground(new MockMultipartFile(
                        "backgroundImg", "background.png", "image/png", new byte[]{1})));

        assertEquals("OSS-9", exception.getErrorCode());
        assertEquals("upload background failed", exception.getErrorMessage());
    }

    @Test
    void getImageViewUrl_shouldThrowBizExceptionWhenResponseIsFail() {
        when(fileFeignApi.getImageViewUrl("image/1/avatar")).thenReturn(Response.fail("OSS-8", "sign failed"));

        BizException exception = assertThrows(BizException.class,
                () -> ossRpcService.getImageViewUrl("image/1/avatar"));

        assertEquals("OSS-8", exception.getErrorCode());
        assertEquals("sign failed", exception.getErrorMessage());
    }
}
