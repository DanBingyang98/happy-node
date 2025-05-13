package com.danby.happynode.auth.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.danby.happynode.auth.constant.RedisKeyConstant;
import com.danby.happynode.auth.enums.ResponseCodeEnum;
import com.danby.happynode.auth.model.vo.verification.VerificationVO;
import com.danby.happynode.auth.service.VerificationService;
import com.danby.happynode.auth.sms.AliyunSmsHelper;
import com.danby.happynode.framework.common.exception.BusinessException;
import com.danby.happynode.framework.common.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class VerificationServiceImpl implements VerificationService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private AliyunSmsHelper aliyunSmsHelper;
    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Override
    public Response<?> sendVerificationCode(VerificationVO verificationVO) {
        String phone = verificationVO.getPhone();
        String verificationCodeKey = RedisKeyConstant.buildVerificationCodeKey(phone);
        Boolean hasKey = redisTemplate.hasKey(verificationCodeKey);
        // 判断是否已发送验证码
        if (hasKey) {
            // 若之前发送的验证码未过期，则提示发送频繁
            throw new BusinessException(ResponseCodeEnum.VERIFICATION_CODE_SEND_FREQUENTLY);
        }
        // 生成 6 位随机数字验证码
        String verificationCode = RandomUtil.randomNumbers(6);
        // 调用第三方短信发送业务
//        threadPoolTaskExecutor.submit(() -> {
//            String signName = "阿里云短信测试";
//            String templateCode = "SMS_154950909";
//            String templateParam = "{\"code\":\"" + verificationCode + "\"}";
//            aliyunSmsHelper.sendMessage(signName, templateCode, phone, templateParam);
//        });
        log.info("发送验证码到手机号{}，验证码为{}", phone, verificationCode);
        // 存储验证码到 redis, 并设置过期时间为 3 分钟
        redisTemplate.opsForValue().set(verificationCodeKey, verificationCode, 3, TimeUnit.MINUTES);
        return Response.success();
    }
}
