package com.zhoushuo.eaqb.user.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.zhoushuo.eaqb.user.biz.domain.mapper")
public class EaqbUserBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(EaqbUserBizApplication.class, args);
    }

}