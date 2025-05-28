package com.danby.happynode.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.danby.framework.context.holder.LoginUserContextHolder;
import com.danby.happynode.auth.constant.RedisKeyConstant;
import com.danby.happynode.auth.constant.RoleConstants;
import com.danby.happynode.auth.domain.dataobject.RoleDO;
import com.danby.happynode.auth.domain.dataobject.UserDO;
import com.danby.happynode.auth.domain.dataobject.UserRoleDO;
import com.danby.happynode.auth.domain.mapper.RoleDOMapper;
import com.danby.happynode.auth.domain.mapper.UserDOMapper;
import com.danby.happynode.auth.domain.mapper.UserRoleDOMapper;
import com.danby.happynode.auth.enums.LoginTypeEnum;
import com.danby.happynode.auth.enums.ResponseCodeEnum;

import com.danby.happynode.auth.model.vo.user.UserLoginReqVO;
import com.danby.happynode.auth.service.UserService;
import com.danby.happynode.framework.common.enums.DeleteEnum;
import com.danby.happynode.framework.common.enums.StatusEnum;
import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.framework.common.util.JsonUtils;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserDOMapper userDOMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private UserRoleDOMapper userRoleDOMapper;

    @Autowired
    private RoleDOMapper roleDOMapper;

    @Override
    public Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO) {
        Long userId = null;
        Integer typeValue = userLoginReqVO.getType();
        LoginTypeEnum type = LoginTypeEnum.valueOf(typeValue);
        switch (type) {
            case VERIFICATION_CODE:
                userId = loginByCode(userLoginReqVO);
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
        Integer redisVerificationCode = (Integer) redisTemplate.opsForValue().get(redisVerificationCodeKey);

        if (redisVerificationCode != null && !StringUtils.equals(redisVerificationCode.toString(), verificationCode)) {
            return null;
        }
        UserDO userDO = userDOMapper.selectByPhone(phone);
        log.info("==> 用户是否注册, phone: {}, userDO: {}", phone, JsonUtils.toJsonString(userDO));
        Long userId = null;
        if (Objects.isNull(userDO)) {
            //注册
            userId = registerUser(phone);
        } else {
            //登录
            userId = userDO.getId();
        }
        return userId;
    }

    private Long loginByPassword(UserLoginReqVO userLoginReqVO) {
        // todo
        return null;
    }

    /**
     * 注册用户
     *
     * @param phone
     * @return
     */
    public Long registerUser(String phone) {
        return transactionTemplate.execute(status -> {
            try {
                // 创建新用户对象
                Long happynodeId = redisTemplate.opsForValue().increment(RedisKeyConstant.HAPPYNODE_ID_GENERATOR_KEY);
                UserDO userDO = UserDO.builder()
                        .happynodeId(String.valueOf(happynodeId))
                        .phone(phone)
                        .nickname("小红薯" + happynodeId)
                        .status(StatusEnum.ENABLED.getValue())
                        .isDeleted(DeleteEnum.NO.getValue())
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .build();
                // 插入数据库
                userDOMapper.insert(userDO);

                // 制造异常 测试事务
//                int i = 1 / 0;

                // 获得新用户对象id
                Long userId = userDO.getId();
                // 分配角色
                UserRoleDO userRoleDO = UserRoleDO.builder()
                        .userId(userId)
                        .roleId(RoleConstants.COMMON_USER_ID)
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .isDeleted(DeleteEnum.NO.getValue())
                        .build();
                userRoleDOMapper.insert(userRoleDO);
                // 将该用户的角色 ID 存入 Redis 中
                RoleDO roleDO = roleDOMapper.selectByPrimaryKey(RoleConstants.COMMON_USER_ID);
                List<String> roles = new ArrayList<>(1);
                roles.add(roleDO.getRoleKey());
                String redisKey = RedisKeyConstant.buildUserRoleKey(userId);
                // 将用户月色信息存入redis 方便后续鉴权
                redisTemplate.opsForValue().set(redisKey, roles);
                return userId;
            } catch (Exception e) {
                status.setRollbackOnly(); // 标记事务为回滚
                log.error("==> 系统注册用户异常: ", e);
                return null;
            }
        });
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
}


