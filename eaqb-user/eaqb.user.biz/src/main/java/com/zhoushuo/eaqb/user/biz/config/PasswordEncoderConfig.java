package com.zhoushuo.eaqb.user.biz.config;

import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 当前采用“auth 验证密码，user 负责密码加密与存储”的方案。
        // 因此 user 与 auth 两个服务的 PasswordEncoder 实现及参数必须保持一致，
        // 否则会出现加密结果与密码校验规则不兼容的问题。
        return new BCryptPasswordEncoder();
    }

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        System.out.println(encoder.encode("qwe123"));
    }
}
