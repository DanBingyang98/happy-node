package com.danby.happynode.user.biz.lsitener;

import cn.hutool.core.collection.CollUtil;
import com.danby.happynode.user.biz.constant.RedisKeyConstant;
import com.danby.happynode.user.biz.domain.dataobject.PermissionDO;
import com.danby.happynode.user.biz.domain.dataobject.RoleDO;
import com.danby.happynode.user.biz.domain.dataobject.RolePermissionDO;
import com.danby.happynode.user.biz.domain.mapper.PermissionDOMapper;
import com.danby.happynode.user.biz.domain.mapper.RoleDOMapper;
import com.danby.happynode.user.biz.domain.mapper.RolePermissionDOMapper;
import com.danby.happynode.framework.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.testng.collections.Maps;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ApplicationReadyListener {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RoleDOMapper roleDOMapper;

    @Autowired
    private PermissionDOMapper permissionDOMapper;

    @Autowired
    private RolePermissionDOMapper rolePermissionDOMapper;

    private static final String PUSH_PERMISSION_FLAG = "push.permission.flag";

    @EventListener(ApplicationReadyEvent.class)
    public void pushPermission2Redis() {
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
                Map<String, List<String>> roleKeyPermissionsMap = Maps.newHashMap();
                roles.forEach(role -> {
                    List<Long> permissionIds = roleIdPermissionIdsMap.get(role.getId());
                    if (CollUtil.isNotEmpty(permissionIds)) {
                        List<String> permissionKeys = permissionIds.stream().map(permissionIdMap::get).filter(Objects::nonNull).map(PermissionDO::getPermissionKey).toList();
                        roleKeyPermissionsMap.put(role.getRoleKey(), permissionKeys);
                    }
                });
                // 存入redis  同步至 Redis 中，方便后续网关查询鉴权使用
                roleKeyPermissionsMap.forEach((roleKey, permissionKeys) -> {
                    String key = RedisKeyConstant.buildRolePermissionsKey(roleKey);
                    redisTemplate.opsForValue().set(key, JsonUtils.toJsonString(permissionKeys));
                });
            }
            log.info("PushRolePermission2RedisRunner succeed");
        } catch (Exception e) {
            log.error("==> PushRolePermission2RedisRunner failed: ", e);
        }
        log.info("PushRolePermission2RedisRunner end");
    }

}
