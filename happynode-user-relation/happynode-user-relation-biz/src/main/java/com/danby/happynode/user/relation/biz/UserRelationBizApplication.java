package com.danby.happynode.user.relation.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("com.danby.happynode.user.relation.biz.domain.mapper")
@EnableFeignClients("com.danby.happynode")
public class UserRelationBizApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserRelationBizApplication.class, args);
    }
}