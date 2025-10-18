package com.zhoushuo.eaqb.auth.sms;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class AliyunSmsClientConfig {

    @Resource
    private AliyunAccessKeyProperties aliyunAccessKeyProperties;

    @Bean
    public com.aliyun.dypnsapi20170525.Client smsClient() {
        try {
            com.aliyun.credentials.Client credential = new com.aliyun.credentials.Client();

            com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                    .setCredential(credential);

            // Endpoint 请参考 https://api.aliyun.com/product/Dypnsapi
            config.endpoint = "dypnsapi.aliyuncs.com";

            config.accessKeyId = aliyunAccessKeyProperties.getAccessKeyId(); // 必填
            config.accessKeySecret = aliyunAccessKeyProperties.getAccessKeySecret(); // 必填

            // 添加日志输出（注意：生产环境中不要打印密钥信息）
            log.info("AccessKeyId: {}", config.accessKeyId);
            log.info("AccessKeySecret: {}", config.accessKeySecret);

            return new com.aliyun.dypnsapi20170525.Client(config);
        } catch (Exception e) {
            log.error("初始化阿里云短信发送客户端错误: ", e);
            return null;
        }
    }


}