package com.zhoushuo.eaqb.user.biz.rpc;

import com.zhoushuo.eaqb.oss.api.FileFeignApi;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * user 服务访问 OSS 服务的 RPC 适配层。
 *
 * <p>这个类不只是简单透传 Feign 调用，还负责把下游 {@link Response}
 * 恢复成当前服务内部可继续传播的异常语义：</p>
 *
 * <ul>
 *   <li>下游响应为空：抛出当前服务定义的 RPC 异常</li>
 *   <li>下游返回失败 DTO：保留下游 errorCode / message 并抛出 {@link BizException}</li>
 *   <li>下游成功但缺少必填字符串数据：抛出当前服务定义的 RPC 异常</li>
 * </ul>
 *
 * <p>这样上层业务只处理“成功结果”或“明确异常”，而不会再把远程失败误判成空值。</p>
 */
@Component
public class OssRpcService {
    private static final String OSS_SERVICE_EMPTY_RESPONSE_ERROR_CODE = "USER-OSS-500";

    @Resource
    private FileFeignApi fileFeignApi;

    /**
     * 调用 OSS 服务上传头像，并返回头像对象路径。
     *
     * <p>若下游失败、响应为空，或未返回对象路径，则直接抛出异常而不是返回 null。</p>
     */
    public String uploadAvatar(MultipartFile file) {
        Response<?> response = fileFeignApi.uploadAvatar(file);
        return requireStringData(response, "OSS 服务未返回头像对象路径");
    }

    /**
     * 调用 OSS 服务上传背景图，并返回背景图对象路径。
     *
     * <p>若下游失败、响应为空，或未返回对象路径，则直接抛出异常而不是返回 null。</p>
     */
    public String uploadBackground(MultipartFile file) {
        Response<?> response = fileFeignApi.uploadBackground(file);
        return requireStringData(response, "OSS 服务未返回背景图对象路径");
    }

    /**
     * 调用 OSS 服务生成图片访问凭证。
     *
     * <p>这里保留的是“远程失败”语义，而不是把失败静默翻译成“用户没有图片”。</p>
     */
    public String getImageViewUrl(String objectKey) {
        Response<String> response = fileFeignApi.getImageViewUrl(objectKey);
        return requireStringData(response, "OSS 服务未返回图片访问凭证");
    }

    /**
     * 统一校验下游返回，并恢复为当前服务内部的字符串结果或异常语义。
     *
     * <p>只有在响应成功且 data 为非空字符串时才返回结果；否则统一抛出异常，
     * 避免上层把远程失败误当成空值处理。</p>
     */
    private String requireStringData(Response<?> response, String emptyDataMessage) {
        if (response == null) {
            throw new BizException(OSS_SERVICE_EMPTY_RESPONSE_ERROR_CODE, "OSS 服务响应为空");
        }
        if (!response.isSuccess()) {
            throw new BizException(response.getErrorCode(), response.getMessage());
        }
        Object data = response.getData();
        if (!(data instanceof String) || StringUtils.isBlank((String) data)) {
            throw new BizException(OSS_SERVICE_EMPTY_RESPONSE_ERROR_CODE, emptyDataMessage);
        }
        return ((String) data).trim();
    }
}