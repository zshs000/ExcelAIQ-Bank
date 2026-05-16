package com.zhoushuo.eaqb.excel.parser.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
@MapperScan("com.zhoushuo.eaqb.excel.parser.biz.domain.mapper")
@EnableFeignClients(basePackages = "com.zhoushuo.eaqb")
@SpringBootApplication
public class EaqbExcelParserBizApplication {
    public static void main(String[] args) {
        SpringApplication.run(EaqbExcelParserBizApplication.class, args);
    }

}
