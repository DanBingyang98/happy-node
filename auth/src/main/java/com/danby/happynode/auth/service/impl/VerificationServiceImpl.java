package com.danby.happynode.auth.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.danby.happynode.auth.constant.RedisKeyConstant;
import com.danby.happynode.auth.enums.ResponseCodeEnum;
import com.danby.happynode.auth.model.vo.verification.VerificationVO;
import com.danby.happynode.auth.service.VerificationService;
import com.danby.happynode.framework.common.exception.BusinessException;
import com.danby.happynode.framework.common.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class VerificationServiceImpl implements VerificationService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Response<?> sendVerificationCode(VerificationVO verificationVO) {
        String phone = verificationVO.getPhone();
        String verificationCodeKey = RedisKeyConstant.buildVerificationCodeKey(phone);
        Boolean hasKey = redisTemplate.hasKey(verificationCodeKey);
        if (hasKey) {
            throw new BusinessException(ResponseCodeEnum.VERIFICATION_CODE_SEND_FREQUENTLY);
        }
        String verificationCode = RandomUtil.randomNumbers(6);
        // todo: 调用第三方短信发送业务

        log.info("发送验证码到手机号{}，验证码为{}", phone, verificationCode);

        redisTemplate.opsForValue().set(verificationCodeKey, verificationCode, 3, TimeUnit.MINUTES);
        return Response.success();
    }
}
