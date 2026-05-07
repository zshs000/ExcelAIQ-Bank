package com.zhoushuo.eaqb.auth.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 当前采用”auth 验证密码，user 负责密码加密与存储”的方案。
        // 因此 auth 与 user 两个服务的 PasswordEncoder 实现及参数必须保持一致，
        // 否则会出现 user 能成功写入密码，但 auth 无法正确校验的兼容性问题。
        return new BCryptPasswordEncoder();
    }

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        log.debug(“BCrypt encoded result: {}”, encoder.encode(“qwe123”));
    }
}
