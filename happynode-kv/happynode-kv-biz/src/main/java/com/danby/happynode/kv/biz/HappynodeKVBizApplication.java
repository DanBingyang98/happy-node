package com.danby.happynode.kv.biz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class HappynodeKVBizApplication {
    public static void main(String[] args) {
        SpringApplication.run(HappynodeKVBizApplication.class, args);
    }
}
