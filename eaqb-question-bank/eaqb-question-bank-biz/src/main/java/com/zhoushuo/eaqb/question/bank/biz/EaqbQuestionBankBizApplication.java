package com.zhoushuo.eaqb.question.bank.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
@MapperScan(basePackages = "com.zhoushuo.eaqb.question.bank.biz.domain.mapper")
@EnableFeignClients(basePackages = "com.zhoushuo.eaqb")
@EnableScheduling
@SpringBootApplication
public class EaqbQuestionBankBizApplication {
    public static void main(String[] args) {
        SpringApplication.run(EaqbQuestionBankBizApplication.class, args);
    }
}
