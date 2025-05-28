package com.danby.happynode.auth.controller;

import com.danby.happynode.auth.model.vo.user.UserLoginReqVO;
import com.danby.happynode.auth.service.UserService;
import com.danby.happynode.framework.biz.operationlog.aspect.ApiOperationLog;
import com.danby.happynode.framework.common.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    @ApiOperationLog(description = "用户登录/注册")
    public Response<String> loginAndRegister(@Validated @RequestBody UserLoginReqVO userLoginReqVO) {
        return userService.loginAndRegister(userLoginReqVO);
    }

    @PostMapping("/logout")
    @ApiOperationLog(description = "用户登出")
    public Response<String> logout(@RequestHeader("userId") String userId) {
        //todo 账号退出登录逻辑待实现
        log.info("==> 网关透传过来的用户 ID: {}", userId);
        return Response.success();
    }
}
