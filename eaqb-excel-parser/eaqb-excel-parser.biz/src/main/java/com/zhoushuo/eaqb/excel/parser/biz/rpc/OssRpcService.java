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

    public String uploadFile(MultipartFile file) {
        // 调用对象存储服务上传文件
        Response<?> response = fileFeignApi.uploadFile(file);

        if (!response.isSuccess()) {
            return null;
        }

        // 返回访问链接
        return (String) response.getData();
    }
    public String getShortUrl(String filePath) {
        log.info("准备调用文件服务获取文件访问链接，文件路径: {}", filePath);
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= SHORT_URL_MAX_ATTEMPTS; attempt++) {
            try {
                Response<String> response = fileFeignApi.getShortUrl(filePath);
                if (response != null && response.isSuccess() && StringUtils.isNotBlank(response.getData())) {
                    log.info("文件服务调用结果: success=true, errorCode=null, attempt={}", attempt);
                    return response.getData();
                }

                String errorCode = response != null ? response.getErrorCode() : "NULL_RESPONSE";
                String message = response != null ? response.getMessage() : "文件服务返回空响应";
                log.warn("获取文件访问链接失败，attempt={}, errorCode={}, message={}",
                        attempt, errorCode, message);
            } catch (RuntimeException e) {
                lastException = e;
                log.warn("获取文件访问链接异常，attempt={}, filePath={}", attempt, filePath, e);
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
