package com.danby.happynode.auth.runner;

import cn.hutool.core.collection.CollUtil;
import com.danby.happynode.auth.constant.RedisKeyConstant;
import com.danby.happynode.auth.domain.dataobject.PermissionDO;
import com.danby.happynode.auth.domain.dataobject.RoleDO;
import com.danby.happynode.auth.domain.dataobject.RolePermissionDO;
import com.danby.happynode.auth.domain.mapper.PermissionDOMapper;
import com.danby.happynode.auth.domain.mapper.RoleDOMapper;
import com.danby.happynode.auth.domain.mapper.RolePermissionDOMapper;
import com.danby.happynode.framework.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.testng.collections.Maps;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PushRolePermission2RedisRunner implements ApplicationRunner {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RoleDOMapper roleDOMapper;

    @Autowired
    private PermissionDOMapper permissionDOMapper;

    @Autowired
    private RolePermissionDOMapper rolePermissionDOMapper;

    private static final String PUSH_PERMISSION_FLAG = "push.permission.flag";

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("PushRolePermission2RedisRunner start");
        try {
            Boolean canPush = redisTemplate.opsForValue().setIfAbsent(PUSH_PERMISSION_FLAG, "1", 1, TimeUnit.DAYS);
            if (Boolean.FALSE.equals(canPush)) {
                log.warn("PushRolePermission2RedisRunner already run");
                return;
            }

            List<RoleDO> roles = roleDOMapper.selectEnabledList();
            if (CollUtil.isNotEmpty(roles)) {
                List<Long> roleIds = roles.stream().map(RoleDO::getId).toList();
                List<RolePermissionDO> rolePermissions = rolePermissionDOMapper.selectByRoleIds(roleIds);
                Map<Long, List<Long>> roleIdPermissionIdsMap = rolePermissions.stream().collect(Collectors.groupingBy(
                        RolePermissionDO::getRoleId, Collectors.mapping(
                                RolePermissionDO::getPermissionId, Collectors.toList()))
                );
                List<PermissionDO> permissions = permissionDOMapper.selectAppEnableList();
                Map<Long, PermissionDO> permissionIdMap = permissions.stream().collect(
                        Collectors.toMap(PermissionDO::getId, permissionDO -> permissionDO)
                );
                // 组织 角色ID-权限 关系
                Map<Long, List<PermissionDO>> roleIdPermissionsMap = Maps.newHashMap();
                roleIds.forEach(roleId -> {
                    List<Long> permissionIds = roleIdPermissionIdsMap.get(roleId);
                    if (CollUtil.isNotEmpty(permissionIds)) {
                        List<PermissionDO> permissionDOs = permissionIds.stream().map(permissionIdMap::get).filter(Objects::nonNull).toList();
                        roleIdPermissionsMap.put(roleId, permissionDOs);
                    }
                });
                // 存入redis  同步至 Redis 中，方便后续网关查询鉴权使用
                roleIdPermissionsMap.forEach((roleId, permissionDOs) -> {
                    String key = RedisKeyConstant.buildRolePermissionsKey(roleId);
                    redisTemplate.opsForValue().set(key, JsonUtils.toJsonString(permissionDOs));
                });
            }
            log.info("PushRolePermission2RedisRunner succeed");
        } catch (Exception e) {
            log.error("==> PushRolePermission2RedisRunner failed: ", e);
        }
        log.info("PushRolePermission2RedisRunner end");
    }
}
