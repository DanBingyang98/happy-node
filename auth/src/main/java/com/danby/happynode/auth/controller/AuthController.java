package com.danby.happynode.auth.controller;

import com.danby.happynode.auth.model.vo.user.UpdatePasswordReqVO;
import com.danby.happynode.auth.model.vo.user.UserLoginReqVO;
import com.danby.happynode.auth.service.AuthService;
import com.danby.happynode.framework.biz.operationlog.aspect.ApiOperationLog;
import com.danby.happynode.framework.common.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    @ApiOperationLog(description = "用户登录/注册")
    public Response<String> loginAndRegister(@Validated @RequestBody UserLoginReqVO userLoginReqVO) {
        return authService.loginAndRegister(userLoginReqVO);
    }

    @PostMapping("/logout")
    @ApiOperationLog(description = "用户登出")
    public Response<?> logout() {
        return authService.logout();
    }

    @PostMapping("/password/update")
    @ApiOperationLog(description = "修改密码")
    public Response<?> updatePassword(@Validated @RequestBody UpdatePasswordReqVO updatePasswordReqVO) {
        return authService.updatePassword(updatePasswordReqVO);
    }
}
