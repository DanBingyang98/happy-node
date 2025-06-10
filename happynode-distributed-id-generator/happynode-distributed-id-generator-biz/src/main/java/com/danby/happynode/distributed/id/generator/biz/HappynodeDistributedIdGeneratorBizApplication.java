package com.danby.happynode.distributed.id.generator.biz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class HappynodeDistributedIdGeneratorBizApplication {
    public static void main(String[] args) {
        SpringApplication.run(HappynodeDistributedIdGeneratorBizApplication.class, args);
    }
}
