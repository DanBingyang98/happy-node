package com.danby.happynode.oss.biz.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorage {
    /**
     * 文件上传
     *
     * @param file
     * @param bucketName
     * @return
     */
    String uploadFile(MultipartFile file, String bucketName);
}
