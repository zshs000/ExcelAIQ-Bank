package com.zhoushuo.eaqb.oss.biz.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "eaqb.oss.presign")
@Component
@Data
public class PresignProperties {

    /**
     * Excel 下载签名链接有效期，单位秒。
     */
    private long excelDownloadExpireSeconds = 600;

    /**
     * 图片查看签名链接有效期，单位秒。
     */
    private long imageViewExpireSeconds = 86400;
}
