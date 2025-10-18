package com.zhoushuo.eaqb.auth;

import com.zhoushuo.eaqb.auth.sms.AliyunAccessKeyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class AliyunAccessKeyPropertiesTest {

    @Autowired
    private AliyunAccessKeyProperties aliyunAccessKeyProperties;

    @Test
    public void testPropertiesLoading() {
        System.out.println("AccessKey ID: " + aliyunAccessKeyProperties.getAccessKeyId());
        System.out.println("AccessKey Secret: " + aliyunAccessKeyProperties.getAccessKeySecret());

        // 断言非空（如果使用默认值，这里可以根据实际情况调整）
        assert aliyunAccessKeyProperties.getAccessKeyId() != null;
        assert aliyunAccessKeyProperties.getAccessKeySecret() != null;
    }
}