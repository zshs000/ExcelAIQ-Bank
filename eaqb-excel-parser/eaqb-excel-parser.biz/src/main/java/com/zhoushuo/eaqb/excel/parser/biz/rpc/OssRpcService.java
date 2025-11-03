package com.zhoushuo.eaqb.excel.parser.biz.rpc;


import com.zhoushuo.eaqb.oss.api.FileFeignApi;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

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
}