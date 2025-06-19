package com.danby.happynode.count.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.danby.happynode.count.biz.domain.mapper")
public class CountBizApplication {
    public static void main(String[] args) {
        SpringApplication.run(CountBizApplication.class, args);
    }
}
