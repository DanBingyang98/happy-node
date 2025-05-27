package com.danby.happynode.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Hello world!
 */

@SpringBootApplication
@EnableDiscoveryClient
public class HappynodeGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(HappynodeGatewayApplication.class, args);

    }
}
