package com.zhoushuo.eaqb.excel.parser.biz.rpc;


import com.zhoushuo.eaqb.oss.api.FileFeignApi;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
@Slf4j
@Component
public class OssRpcService {

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
        log.info("准备调用文件服务获取短链接，文件路径: {}", filePath);
        Response<String> response = fileFeignApi.getShortUrl(filePath);
        log.info("文件服务调用结果: success={}, data={}, errorCode={}",
                response.isSuccess(), response.getData(), response.getErrorCode());

        if (!response.isSuccess()) {
            log.error("获取短链接失败: {}", response.getMessage());
            return null;
        }

        return response.getData();
    }
}