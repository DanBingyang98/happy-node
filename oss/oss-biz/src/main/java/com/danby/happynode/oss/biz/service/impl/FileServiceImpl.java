package com.danby.happynode.oss.biz.service.impl;

import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.oss.biz.service.FileService;
import com.danby.happynode.oss.biz.storage.FileStorage;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RefreshScope
public class FileServiceImpl implements FileService {
    @Autowired
    private FileStorage fileStorage;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Override
    public Response<?> uploadFile(MultipartFile file) {
        // 上传文件
        String url = fileStorage.uploadFile(file, bucketName);
        return Response.success(url);
    }
}
