package com.danby.happynode.gateway.auth;

import cn.dev33.satoken.stp.StpInterface;

import cn.hutool.core.collection.CollUtil;
import com.danby.happynode.gateway.constant.RedisKeyConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class StpInterfaceImpl implements StpInterface {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @SneakyThrows
    public List<String> getPermissionList(Object loginId, String loginType) {
        log.info("## 获取用户权限列表, loginId: {}", loginId);
        // 返回此 loginId 拥有的权限列表
        // todo 从 redis 获取
        String redisUserRolesKey = RedisKeyConstants.buildUserRoleKey(Long.valueOf(loginId.toString()));

        String redisUserRoleValue = redisTemplate.opsForValue().get(redisUserRolesKey);

        if (StringUtils.isBlank(redisUserRoleValue)) {
            return null;
        }

        List<String> userRoles = objectMapper.readValue(redisUserRoleValue, new TypeReference<>() {
        });

        if (CollUtil.isNotEmpty(userRoles)) {
            List<String> rolePermissionsKeys = userRoles.stream().map(RedisKeyConstants::buildRolePermissionsKey).toList();
            //一次性将这些角色对应的权限标识符查询出来，保证最少的 IO 次数（与 Redis 只交互 2 次），提升查询性能
            List<String> rolePermissionsValue = redisTemplate.opsForValue().multiGet(rolePermissionsKeys);
            if (CollUtil.isNotEmpty(rolePermissionsValue)) {
                List<String> permissions = rolePermissionsValue.stream().flatMap(eachPermissionsJsonStr -> {
                    try {
                        List<String> eachPermissions = objectMapper.readValue(eachPermissionsJsonStr, new TypeReference<>() {
                        });
                        return eachPermissions.stream();
                    } catch (JsonProcessingException e) {
                        log.error("==> JSON 解析错误: ", e);
                        return null;
                    }
                }).collect(Collectors.toList());
                log.info("## <UNK>: {}", permissions);
                return permissions;
            }
        }
        return null;
    }

    @SneakyThrows
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        log.info("## 获取用户角色列表, loginId: {}", loginId);
        // 返回此 loginId 拥有的角色列表
        // todo 从 redis 获取
        // 构建 用户-角色 Redis Key
        String redisUserRolesKey = RedisKeyConstants.buildUserRoleKey(Long.valueOf(loginId.toString()));
        // 根据用户 ID ，从 Redis 中获取该用户的角色集合
        String userRolesValueStr = redisTemplate.opsForValue().get(redisUserRolesKey);

        if (StringUtils.isBlank(userRolesValueStr)) {
            return null;
        }
        return objectMapper.readValue(userRolesValueStr, new TypeReference<>() {
        });
    }
}
