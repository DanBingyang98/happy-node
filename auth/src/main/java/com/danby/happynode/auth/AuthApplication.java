package com.danby.happynode.auth;

import com.alibaba.cloud.nacos.configdata.NacosConfigDataLoader;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@MapperScan("com.danby.happynode.auth.domain.mapper")
@EnableDiscoveryClient
public class AuthApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext applicationContext = SpringApplication.run(AuthApplication.class, args);
        String property = applicationContext.getEnvironment().getProperty("alarm.type");
        System.out.println("property = " + property);
//        NacosConfigDataLoader nacosConfigDataLoader = applicationContext.getBean(NacosConfigDataLoader.class);
//        System.out.println("nacosConfigDataLoader = " + nacosConfigDataLoader);
    }

}