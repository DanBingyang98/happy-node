package com.danby.happynode.oss.biz.storage.impl;

import com.danby.happynode.oss.biz.storage.FileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
public class AliyunOSSFileStorage implements FileStorage {

    @Override
    public String uploadFile(MultipartFile file, String bucketName) {
        return "";
    }
}
