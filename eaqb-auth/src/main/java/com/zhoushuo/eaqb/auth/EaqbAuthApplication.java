package com.zhoushuo.eaqb.auth;


import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@MapperScan("com.zhoushuo.eaqb.auth.domain.mapper")
@SpringBootApplication

public class EaqbAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(EaqbAuthApplication.class, args);
    }

}
