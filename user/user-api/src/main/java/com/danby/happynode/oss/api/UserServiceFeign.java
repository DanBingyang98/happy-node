package com.danby.happynode.oss.api;

import com.danby.happynode.framework.common.response.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient
public interface UserServiceFeign {
    @PostMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Response<?> updateUserInfo(@Validated UpdateUserInfoReqVO updateUserInfoReqVO);
}
