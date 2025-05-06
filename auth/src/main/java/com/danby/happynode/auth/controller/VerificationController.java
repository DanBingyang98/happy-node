package com.danby.happynode.auth.controller;

import com.danby.happynode.auth.model.vo.verification.VerificationVO;
import com.danby.happynode.auth.service.VerificationService;
import com.danby.happynode.framework.biz.operationlog.aspect.ApiOperationLog;
import com.danby.happynode.framework.common.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VerificationController {

    @Autowired
    private VerificationService verificationService;

    @PostMapping("/verify/phone")
    @ApiOperationLog(description = "发送短信验证码")
    public Response<?> verify(@RequestBody @Validated VerificationVO verificationVO) {
        return verificationService.sendVerificationCode(verificationVO);
    }

}
