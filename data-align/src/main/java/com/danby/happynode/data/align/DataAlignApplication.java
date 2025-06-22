package com.danby.happynode.data.align;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class DataAlignApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataAlignApplication.class, args);
    }
}
