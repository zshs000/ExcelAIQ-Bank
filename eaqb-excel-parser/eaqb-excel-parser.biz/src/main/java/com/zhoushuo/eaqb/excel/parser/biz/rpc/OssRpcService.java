package com.zhoushuo.eaqb.excel.parser.biz.rpc;

import com.zhoushuo.eaqb.oss.api.FileFeignApi;
import com.zhoushuo.framework.commono.exception.BizException;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

/**
 * excel-parser 服务访问 OSS 服务的 RPC 适配层。
 *
 * <p>该类负责两件事：</p>
 *
 * <ul>
 *   <li>把上传文件、获取下载访问凭证这些 Feign 调用封装成当前服务可直接使用的方法</li>
 *   <li>把下游 {@link Response} 恢复成当前服务内部的异常语义，避免远程失败被压成 null</li>
 * </ul>
 *
 * <p>其中下载访问凭证场景带有有限次重试，但重试完成后仍必须保留最终失败原因，
 * 而不是把错误语义吞掉。</p>
 */
@Slf4j
@Component
public class OssRpcService {
    private static final int SHORT_URL_MAX_ATTEMPTS = 3;
    private static final long SHORT_URL_RETRY_DELAY_MS = 100L;
    private static final String OSS_SERVICE_EMPTY_RESPONSE_ERROR_CODE = "EXCEL-OSS-500";

    @Resource
    private FileFeignApi fileFeignApi;

    /**
     * 调用 OSS 服务上传 Excel 文件，并返回对象路径。
     *
     * <p>若下游失败、响应为空，或未返回对象路径，则直接抛出异常而不是返回 null。</p>
     */
    public String uploadExcel(MultipartFile file, String objectName) {
        Response<?> response = fileFeignApi.uploadExcel(file, objectName);
        return requireStringData(response, "文件服务未返回对象路径");
    }

    /**
     * 调用 OSS 服务获取 Excel 下载访问凭证。
     *
     * <p>该方法会对瞬时失败做有限次重试；若重试后仍失败，则抛出最后一次异常，
     * 保留下游真实错误语义。</p>
     */
    public String getExcelDownloadUrl(String objectKey) {
        log.info("准备调用文件服务获取 Excel 下载访问凭证，对象路径: {}", objectKey);
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= SHORT_URL_MAX_ATTEMPTS; attempt++) {
            try {
                Response<String> response = fileFeignApi.getExcelDownloadUrl(objectKey);
                String downloadUrl = requireStringData(response, "文件服务未返回 Excel 下载访问凭证");
                log.info("文件服务调用结果: success=true, errorCode=null, attempt={}", attempt);
                return downloadUrl;
            } catch (RuntimeException e) {
                lastException = e;
                if (e instanceof BizException bizException) {
                    log.warn("获取 Excel 下载访问凭证失败，attempt={}, errorCode={}, message={}",
                            attempt, bizException.getErrorCode(), bizException.getErrorMessage());
                } else {
                    log.warn("获取 Excel 下载访问凭证异常，attempt={}, objectKey={}", attempt, objectKey, e);
                }
            }

            if (attempt < SHORT_URL_MAX_ATTEMPTS) {
                sleepBeforeRetry(attempt);
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        throw new BizException(OSS_SERVICE_EMPTY_RESPONSE_ERROR_CODE, "文件服务重试后仍未返回有效响应");
    }

    /**
     * 统一校验下游返回，并恢复为当前服务内部的字符串结果或异常语义。
     *
     * <p>只有在响应成功且 data 为非空字符串时才返回结果；否则统一抛出异常，
     * 避免上层把远程失败误当成空值处理。</p>
     */
    private String requireStringData(Response<?> response, String emptyDataMessage) {
        if (response == null) {
            throw new BizException(OSS_SERVICE_EMPTY_RESPONSE_ERROR_CODE, "文件服务响应为空");
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

    /**
     * 在有限次重试之间短暂等待，降低瞬时故障下的连续打满概率。
     */
    private void sleepBeforeRetry(int attempt) {
        try {
            TimeUnit.MILLISECONDS.sleep(SHORT_URL_RETRY_DELAY_MS * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待文件服务重试时被中断", e);
        }
    }
}