package com.danby.happynode.user.biz.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.danby.framework.context.holder.LoginUserContextHolder;
import com.danby.happynode.framework.common.enums.DeleteEnum;
import com.danby.happynode.framework.common.enums.StatusEnum;
import com.danby.happynode.framework.common.exception.BusinessException;
import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.framework.common.util.JsonUtils;
import com.danby.happynode.framework.common.util.ParamUtils;
import com.danby.happynode.user.biz.constant.RedisKeyConstant;
import com.danby.happynode.user.biz.constant.RoleConstants;
import com.danby.happynode.user.biz.domain.dataobject.RoleDO;
import com.danby.happynode.user.biz.domain.dataobject.UserDO;
import com.danby.happynode.user.biz.domain.mapper.RoleDOMapper;
import com.danby.happynode.user.biz.domain.mapper.UserDOMapper;
import com.danby.happynode.user.biz.enums.ResponseCodeEnum;
import com.danby.happynode.user.biz.enums.SexEnum;
import com.danby.happynode.user.biz.model.vo.UpdateUserInfoReqVO;
import com.danby.happynode.user.biz.rpc.DistributedIdGeneratorRpcService;
import com.danby.happynode.user.biz.rpc.OssRpcService;
import com.danby.happynode.user.biz.service.UserService;
import com.danby.happynode.user.dto.req.FindUserByIdReqDTO;
import com.danby.happynode.user.dto.req.FindUserByPhoneReqDTO;
import com.danby.happynode.user.dto.req.RegisterUserReqDTO;
import com.danby.happynode.user.dto.req.UpdateUserPasswordReqDTO;
import com.danby.happynode.user.dto.resp.FindUserByIdRespDTO;
import com.danby.happynode.user.dto.resp.FindUserByPhoneRespDTO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserDOMapper userDOMapper;

    @Autowired
    private OssRpcService ossRpcService;

    @Autowired
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;

    @Autowired
    private RoleDOMapper roleDOMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    /**
     * 用户信息本地缓存
     */
    private static final Cache<Long, FindUserByIdRespDTO> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(10000) // 设置初始容量为 10000 个条目
            .maximumSize(10000) // 设置缓存的最大容量为 10000 个条目
            .expireAfterWrite(1, TimeUnit.HOURS) // 设置缓存条目在写入后 1 小时过期
            .build();

    /**
     * 更新用户信息
     *
     * @param updateUserInfoReqVO
     * @return
     */
    @Override
    public Response<?> updateUserInfo(UpdateUserInfoReqVO updateUserInfoReqVO) {
        UserDO userDO = new UserDO();
        // 设置当前需要更新的用户 ID
        userDO.setId(LoginUserContextHolder.getUserId());
        // 标识位：是否需要更新
        boolean needUpdate = false;
        // 头像
        MultipartFile avatarFile = updateUserInfoReqVO.getAvatar();

        if (Objects.nonNull(avatarFile)) {
            String avatar = ossRpcService.uploadFile(avatarFile);
            log.info("==> 调用 oss 服务成功，上传头像，url：{}", avatar);
            if (StringUtils.isBlank(avatar)) {
                throw new BusinessException(ResponseCodeEnum.UPLOAD_AVATAR_FAIL);
            } else {
                userDO.setAvatar(avatar);
                needUpdate = true;
            }
        }

        // 昵称
        String nickname = updateUserInfoReqVO.getNickname();
        if (StringUtils.isNotBlank(nickname)) {
            Preconditions.checkArgument(ParamUtils.checkNickname(nickname), ResponseCodeEnum.NICK_NAME_VALID_FAIL.getMessage());
            userDO.setNickname(nickname);
            needUpdate = true;
        }

        // 小哈书号
        String happynodeId = updateUserInfoReqVO.getHappynodeId();
        if (StringUtils.isNotBlank(happynodeId)) {
            Preconditions.checkArgument(ParamUtils.checkHappynodeId(happynodeId), ResponseCodeEnum.HAPPYNODE_ID_VALID_FAIL.getMessage());
            userDO.setHappynodeId(happynodeId);
            needUpdate = true;
        }

        // 性别
        Integer sex = updateUserInfoReqVO.getSex();
        if (Objects.nonNull(sex)) {
            Preconditions.checkArgument(SexEnum.isValid(sex), ResponseCodeEnum.SEX_VALID_FAIL.getMessage());
            userDO.setSex(sex);
            needUpdate = true;
        }

        // 生日
        LocalDate birthday = updateUserInfoReqVO.getBirthday();
        if (Objects.nonNull(birthday)) {
            userDO.setBirthday(birthday);
            needUpdate = true;
        }

        // 个人简介
        String introduction = updateUserInfoReqVO.getIntroduction();
        if (StringUtils.isNotBlank(introduction)) {
            Preconditions.checkArgument(ParamUtils.checkLength(introduction, 100), ResponseCodeEnum.INTRODUCTION_VALID_FAIL.getMessage());
            userDO.setIntroduction(introduction);
            needUpdate = true;
        }

        // 背景图
        MultipartFile backgroundImgFile = updateUserInfoReqVO.getBackgroundImg();
        if (Objects.nonNull(backgroundImgFile)) {
            String background = ossRpcService.uploadFile(backgroundImgFile);
            log.info("==> 调用 oss 服务成功，上传背景图，url：{}", background);
            if (StringUtils.isBlank(background)) {
                throw new BusinessException(ResponseCodeEnum.UPLOAD_BACKGROUND_IMG_FAIL);
            } else {
                userDO.setBackgroundImg(background);
                needUpdate = true;
            }
        }

        if (needUpdate) {
            // 更新用户信息
            userDO.setUpdateTime(LocalDateTime.now());
            userDOMapper.updateByPrimaryKeySelective(userDO);
        }
        return Response.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<Long> register(RegisterUserReqDTO registerUserReqDTO) {
        String phone = registerUserReqDTO.getPhone();
        UserDO userDO = userDOMapper.selectByPhone(phone);
        if (Objects.nonNull(userDO)) {
            return Response.success(userDO.getId());
        }
//        Long newHappynodeId = redisTemplate.opsForValue().increment(RedisKeyConstant.HAPPYNODE_ID_GENERATOR_KEY);
        String userId = distributedIdGeneratorRpcService.getUserId();
        String happynodeId = distributedIdGeneratorRpcService.getHappynodeId();

        UserDO newUserDO = UserDO.builder()
                .id(Long.valueOf(userId))
                .phone(phone)
                .happynodeId(String.valueOf(happynodeId))
                .nickname("小红薯" + happynodeId)
                .status(StatusEnum.ENABLED.getValue()) // 状态为启用
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDeleted(DeleteEnum.NO.getValue()) // 逻辑删除
                .build();
        userDOMapper.insert(newUserDO);
        Long userDOId = newUserDO.getId();

        RoleDO roleDO = roleDOMapper.selectByPrimaryKey(RoleConstants.COMMON_USER_ROLE_ID);
        ArrayList<String> roles = new ArrayList<>(1);
        roles.add(roleDO.getRoleKey());

        String userRolesKey = RedisKeyConstant.buildUserRoleKey(userDOId);
        redisTemplate.opsForValue().set(userRolesKey, roles);

        return Response.success(userDOId);
    }

    @Override
    public Response<FindUserByPhoneRespDTO> findByPhone(FindUserByPhoneReqDTO findUserByPhoneReqDTO) {
        String phone = findUserByPhoneReqDTO.getPhone();
        UserDO userDO = userDOMapper.selectByPhone(phone);
        if (Objects.isNull(userDO)) {
            throw new BusinessException(ResponseCodeEnum.USER_NOT_FOUND);
        } else {
            FindUserByPhoneRespDTO findUserByPhoneRespDTO = FindUserByPhoneRespDTO.builder()
                    .id(userDO.getId())
                    .password(userDO.getPassword())
                    .build();
            return Response.success(findUserByPhoneRespDTO);
        }


    }

    @Override
    public Response<?> updatePassword(UpdateUserPasswordReqDTO updateUserPasswordReqDTO) {
        Long userId = LoginUserContextHolder.getUserId();
        String encodePassword = updateUserPasswordReqDTO.getEncodePassword();
        UserDO userDO = UserDO.builder()
                .id(userId)
                .password(encodePassword)
                .updateTime(LocalDateTime.now())
                .build();
        userDOMapper.updateByPrimaryKeySelective(userDO);
        return Response.success();
    }

    @Override
    public Response<FindUserByIdRespDTO> findById(FindUserByIdReqDTO findUserByIdReqDTO) {
        Long id = findUserByIdReqDTO.getId();

        FindUserByIdRespDTO findUserByIdRespDTOLocalCache = LOCAL_CACHE.getIfPresent(id);
        if (Objects.nonNull(findUserByIdRespDTOLocalCache)) {
            log.info("==> 命中了本地缓存；{}", findUserByIdRespDTOLocalCache);
            return Response.success(findUserByIdRespDTOLocalCache);
        }

        // 用户缓存key
        String userInfoRedisKey = RedisKeyConstant.buildUserInfoKey(id);

        // 再从 Redis 缓存中查询
        String userInfoRedisValue = (String) redisTemplate.opsForValue().get(userInfoRedisKey);

        // 若 Redis 缓存中存在该用户信息
        if (StringUtils.isNotBlank(userInfoRedisValue)) {
            // 将存储的 Json 字符串转换成对象，并返回
            FindUserByIdRespDTO findUserByIdRespDTO = JsonUtils.parseObject(userInfoRedisValue, FindUserByIdRespDTO.class);
            // 异步线程中将用户信息存入本地缓存
            threadPoolTaskExecutor.submit(() -> {
                if (Objects.nonNull(findUserByIdRespDTO)) {
                    LOCAL_CACHE.put(id, findUserByIdRespDTO);
                }
            });
            return Response.success(findUserByIdRespDTO);
        }

        // 否则, 从数据库中查询
        // 根据用户 ID 查询用户信息
        UserDO userDO = userDOMapper.selectByPrimaryKey(id);

        if (Objects.isNull(userDO)) {
            threadPoolTaskExecutor.execute(() -> {
                // 防止穿透，将空数据存入redis
                long expireSeconds = 60 + RandomUtil.randomInt(60);
                redisTemplate.opsForValue().set(userInfoRedisKey, "null", expireSeconds, TimeUnit.SECONDS);
            });
            throw new BusinessException(ResponseCodeEnum.USER_NOT_FOUND);
        }

        FindUserByIdRespDTO findUserByIdRespDTO = FindUserByIdRespDTO.builder()
                .id(userDO.getId())
                .avatar(userDO.getAvatar())
                .nickName(userDO.getNickname())
                .build();
        threadPoolTaskExecutor.submit(() -> {
            long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
            redisTemplate.opsForValue().set(userInfoRedisKey, JsonUtils.toJsonString(findUserByIdRespDTO), expireSeconds, TimeUnit.SECONDS);
        });
        return Response.success(findUserByIdRespDTO);
    }
}
