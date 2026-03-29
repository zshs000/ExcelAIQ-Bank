package com.zhoushuo.eaqb.excel.parser.biz.rpc;


import com.zhoushuo.eaqb.oss.api.FileFeignApi;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class OssRpcService {
    private static final int SHORT_URL_MAX_ATTEMPTS = 3;
    private static final long SHORT_URL_RETRY_DELAY_MS = 100L;

    @Resource
    private FileFeignApi fileFeignApi;

    public String uploadExcel(MultipartFile file, String objectName) {
        Response<?> response = fileFeignApi.uploadExcel(file, objectName);

        if (response == null || !response.isSuccess()) {
            return null;
        }

        return (String) response.getData();
    }

    public String getPresignedDownloadUrl(String objectKey) {
        log.info("准备调用文件服务获取文件下载访问凭证，对象路径: {}", objectKey);
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= SHORT_URL_MAX_ATTEMPTS; attempt++) {
            try {
                Response<String> response = fileFeignApi.getPresignedDownloadUrl(objectKey);
                if (response != null && response.isSuccess() && StringUtils.isNotBlank(response.getData())) {
                    log.info("文件服务调用结果: success=true, errorCode=null, attempt={}", attempt);
                    return response.getData();
                }

                String errorCode = response != null ? response.getErrorCode() : "NULL_RESPONSE";
                String message = response != null ? response.getMessage() : "文件服务返回空响应";
                log.warn("获取文件下载访问凭证失败，attempt={}, errorCode={}, message={}",
                        attempt, errorCode, message);
            } catch (RuntimeException e) {
                lastException = e;
                log.warn("获取文件下载访问凭证异常，attempt={}, objectKey={}", attempt, objectKey, e);
            }

            if (attempt < SHORT_URL_MAX_ATTEMPTS) {
                sleepBeforeRetry(attempt);
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        return null;
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            TimeUnit.MILLISECONDS.sleep(SHORT_URL_RETRY_DELAY_MS * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待文件服务重试时被中断", e);
        }
    }
}
