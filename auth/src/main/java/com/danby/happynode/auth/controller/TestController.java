package com.danby.happynode.auth.controller;

import com.danby.happynode.framework.biz.operationlog.aspect.ApiOperationLog;
import com.danby.happynode.framework.common.response.Response;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.http.HttpRequest;
import java.time.LocalDateTime;

@RestController
public class TestController {

    @GetMapping("/test/{name}")
    @ApiOperationLog(description = "测试接口")
    public Response<String> test(@PathVariable("name") String name) {
        return Response.success("test! " + name);
    }

    @GetMapping("/test2/{name}")
    @ApiOperationLog(description = "测试接口 测试localDateTime")
    public Response<User> test2(@PathVariable("name") String name) {

        return Response.success(User.builder()
                .nickName(name)
                .createTime(LocalDateTime.now())
                .build());
    }

    @PostMapping("/test3")
    @ApiOperationLog(description = "测试接口3 传入localdatetime测试")
    public Response<User> test3(@RequestBody @Validated User user) {
        return Response.success(user);
    }

}
