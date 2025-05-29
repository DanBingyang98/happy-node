package com.danby.happynode.oss.biz.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
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
public class FileOSSConfig {

    @Value("${minio.endpoint}")
    private String minioEndpoint;
    @Value("${minio.access-key}")
    private String minioAccessKey;
    @Value("${minio.secret-key}")
    private String minioSecretKey;

    @Value("${aliyun.endpoint}")
    private String aliyunEndpoint;
    @Value("${aliyun.access-key}")
    private String aliyunAccessKey;
    @Value("${aliyun.secret-key}")
    private String aliyunSecretKey;

    @Bean
    @ConditionalOnProperty(name = "storage.aliyun", havingValue = "aliyun")
    public OSS aliyunOSSClient() {
        // 设置访问凭证
        DefaultCredentialProvider credentialsProvider = CredentialsProviderFactory.newDefaultCredentialProvider(
                aliyunAccessKey, aliyunSecretKey);
        // 创建 OSSClient 实例
        return new OSSClientBuilder().build(aliyunEndpoint, credentialsProvider);
    }

    @Bean
    @ConditionalOnProperty(name = "storage.type", havingValue = "minio")
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(minioEndpoint)
                .credentials(minioAccessKey, minioSecretKey)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "storage.type", havingValue = "aliyun")
    public FileStorage aliyunFileStrategy() {
        return new AliyunOSSFileStorage();
    }

    @Bean
    @ConditionalOnProperty(name = "storage.type", havingValue = "minio")
    public FileStorage minioFileStrategy() {
        return new MinioFileStorage();
    }
}
