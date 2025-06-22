package com.danby.happynode.data.align;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.danby.happynode.data.align.domain.mapper")
public class DataAlignApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataAlignApplication.class, args);
    }
}
