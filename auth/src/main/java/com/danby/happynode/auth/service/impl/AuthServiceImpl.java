package com.danby.happynode.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.danby.framework.context.holder.LoginUserContextHolder;
import com.danby.happynode.auth.constant.RedisKeyConstant;
import com.danby.happynode.auth.enums.LoginTypeEnum;
import com.danby.happynode.auth.enums.ResponseCodeEnum;
import com.danby.happynode.auth.model.vo.user.UpdatePasswordReqVO;
import com.danby.happynode.auth.model.vo.user.UserLoginReqVO;
import com.danby.happynode.auth.rpc.UserRpcService;
import com.danby.happynode.auth.service.AuthService;
import com.danby.happynode.framework.common.exception.BusinessException;
import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.user.dto.resp.FindUserByPhoneRespDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRpcService userRpcService;

    @Override
    public Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO) {
        Long userId = null;
        Integer typeValue = userLoginReqVO.getType();
        LoginTypeEnum type = LoginTypeEnum.valueOf(typeValue);

        // 登录类型错误
        if (Objects.isNull(type)) {
            throw new BusinessException(ResponseCodeEnum.LOGIN_TYPE_ERROR);
        }
        switch (type) {
            case VERIFICATION_CODE:
                userId = loginByCode(userLoginReqVO);
                if (Objects.isNull(userId)) {
                    throw new BusinessException(ResponseCodeEnum.LOGIN_FAIL);
                }
                break;
            case PASSWORD:
                userId = loginByPassword(userLoginReqVO);
                break;
        }
        if (Objects.isNull(userId)) {
            return Response.fail(ResponseCodeEnum.VERIFICATION_CODE_ERROR);
        }
        // Satoken 登录用户 入参为用户id
        StpUtil.login(userId);
        // 获得登录后的token信息
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        // 返回token信息
        return Response.success(tokenInfo.tokenValue);
    }


    private Long loginByCode(UserLoginReqVO userLoginReqVO) {
        String phone = userLoginReqVO.getPhone();
        String verificationCode = userLoginReqVO.getCode();
//        Preconditions.checkArgument(StringUtils.isBlank(verificationCode),"验证码为空");
        if (StringUtils.isBlank(verificationCode)) {
            throw new IllegalArgumentException("验证码为空");
        }
        String redisVerificationCodeKey = RedisKeyConstant.buildVerificationCodeKey(phone);
        String redisVerificationCode = (String) redisTemplate.opsForValue().get(redisVerificationCodeKey);

        if (redisVerificationCode != null && !StringUtils.equals(redisVerificationCode.toString(), verificationCode)) {
            return null;
        }

        return userRpcService.registerUser(phone);
    }

    private Long loginByPassword(UserLoginReqVO userLoginReqVO) {
        String loginPassword = userLoginReqVO.getPassword();
        String phone = userLoginReqVO.getPhone();
        FindUserByPhoneRespDTO userByPhone = userRpcService.findUserByPhone(phone);
        String dbPassword = userByPhone.getPassword();
        boolean matches = passwordEncoder.matches(loginPassword, dbPassword);
        if (!matches) {
            throw new BusinessException(ResponseCodeEnum.PHONE_OR_PASSWORD_ERROR);
        }

        return userByPhone.getId();
    }

    /**
     * 退出登录
     *
     * @return Response<String>
     */
    public Response<?> logout() {
        // 退出登录 (指定用户 ID)
        Long userId = LoginUserContextHolder.getUserId();
        log.info("==> 用户退出登录, userId: {}", userId);
        StpUtil.logout(userId);
        return Response.success();
    }

    @Override
    public Response<?> updatePassword(UpdatePasswordReqVO updatePasswordReqVO) {
        String newPassword = updatePasswordReqVO.getNewPassword();
        String encodePassword = passwordEncoder.encode(newPassword);
        return userRpcService.updatePassword(encodePassword);
    }
}


