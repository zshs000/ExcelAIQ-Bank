package com.zhoushuo.eaqb.user.biz.rpc;

import com.zhoushuo.eaqb.oss.api.FileFeignApi;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class OssRpcService {

    @Resource
    private FileFeignApi fileFeignApi;

    public String uploadAvatar(MultipartFile file) {
        Response<?> response = fileFeignApi.uploadAvatar(file);
        if (!response.isSuccess()) {
            return null;
        }
        return (String) response.getData();
    }

    public String uploadBackground(MultipartFile file) {
        Response<?> response = fileFeignApi.uploadBackground(file);
        if (!response.isSuccess()) {
            return null;
        }
        return (String) response.getData();
    }

    public String getImageViewUrl(String objectKey) {
        Response<String> response = fileFeignApi.getImageViewUrl(objectKey);
        if (response == null || !response.isSuccess()) {
            return null;
        }
        return response.getData();
    }
}
