package com.zhoushuo.eaqb.auth;



import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;


@SpringBootApplication
@EnableFeignClients(basePackages = "com.zhoushuo.eaqb")

public class EaqbAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(EaqbAuthApplication.class, args);
    }

}
