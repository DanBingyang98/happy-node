package com.danby.happynode.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.danby.happynode")
public class AuthApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext applicationContext = SpringApplication.run(AuthApplication.class, args);
        String property = applicationContext.getEnvironment().getProperty("alarm.type");
        System.out.println("property = " + property);
    }

}