package com.danby.happynode.auth.service;

import com.danby.happynode.auth.model.vo.verification.VerificationVO;
import com.danby.happynode.framework.common.response.Response;

public interface VerificationService {
    Response<?> sendVerificationCode(VerificationVO verificationVO);
}
