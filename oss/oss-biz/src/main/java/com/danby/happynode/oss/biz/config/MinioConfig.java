package com.danby.happynode.oss.biz.config;

import com.danby.happynode.oss.biz.storage.FileStorage;
import com.danby.happynode.oss.biz.storage.impl.AliyunOSSFileStorage;
import com.danby.happynode.oss.biz.storage.impl.MinioFileStorage;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RefreshScope
public class MinioConfig {

    @Value("${minio.endpoint}")
    private String endpoint;
    @Value("${minio.access-key}")
    private String accessKey;
    @Value("${minio.secret-key}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "storage.type",havingValue = "aliyun")
    public FileStorage aliyunFileStrategy() {
        return new AliyunOSSFileStorage();
    }

    @Bean
    @ConditionalOnProperty(name = "storage.type",havingValue = "minio")
    public FileStorage minioFileStrategy() {
        return new MinioFileStorage();
    }
}
