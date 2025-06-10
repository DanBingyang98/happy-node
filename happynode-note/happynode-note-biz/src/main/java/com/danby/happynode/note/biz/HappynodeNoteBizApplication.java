package com.danby.happynode.note.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan(basePackages = "com.danby.happynode.note.biz.domain.mapper")
@EnableFeignClients(basePackages = "com.danby.happynode")
public class HappynodeNoteBizApplication {
    public static void main(String[] args) {
        SpringApplication.run(HappynodeNoteBizApplication.class, args);
    }
}
