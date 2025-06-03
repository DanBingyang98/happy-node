package com.danby.happynode.user.biz.rpc;

import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.oss.api.feign.FileFeign;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class OssRpcService {
    @Autowired
    private FileFeign fileFeign;

    public String uploadFile(MultipartFile file) {
        Response<?> upload = fileFeign.upload(file);
        if (upload.isSuccess()) {
            return upload.getData().toString();
        } else {
            return null;
        }
    }
}
