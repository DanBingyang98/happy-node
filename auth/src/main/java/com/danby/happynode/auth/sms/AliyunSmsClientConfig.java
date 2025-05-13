package com.danby.happynode.auth.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.teaopenapi.models.Config;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AliyunSmsClientConfig {
    @Resource
    AliyunAccessKeyProperties properties;

    @Bean
    public Client smsClient() {
        try {
            Config config = new Config()
                    // 必填
                    .setAccessKeyId(properties.getAccessKeyId())
                    // 必填
                    .setAccessKeySecret(properties.getAccessKeySecret());

            // Endpoint 请参考 https://api.aliyun.com/product/Dysmsapi
            config.endpoint = "dysmsapi.aliyuncs.com";

            return new Client(config);
        } catch (Exception e) {
            log.error("初始化阿里云短信发送客户端错误: ", e);
            return null;
        }
    }
}
