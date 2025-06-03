package com.danby.happynode.oss.api.feign;

import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.oss.api.config.FeignFormConfig;
import com.danby.happynode.oss.api.constants.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(name = ApiConstants.SERVICE_NAME, path = ApiConstants.SERVICE_PREFIX, configuration = FeignFormConfig.class)
public interface FileFeign {

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Response<?> upload(@RequestPart("file") MultipartFile file);
}
