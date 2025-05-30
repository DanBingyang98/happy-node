package com.danby.happynode.user.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.danby.happynode.user.biz.domain.mapper")
public class UserBizApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserBizApplication.class, args);
    }
}
