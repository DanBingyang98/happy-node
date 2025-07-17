package com.danby.happynode.user.biz.constant;

public class RedisKeyConstant {

    /**
     * HappyNode 全局 ID 生成器 KEY
     */
    public static final String HAPPYNODE_ID_GENERATOR_KEY = "happynode.id.generator";

    /**
     * 用户角色 KEY 前缀
     */
    public static final String USER_ROLE_KEY_PREFIX = "user:roles:";

    /**
     * 用户信息数据 KEY 前缀
     */
    private static final String USER_INFO_KEY_PREFIX = "user:info:";

    /**
     * 用户主页信息数据 KEY 前缀
     */
    private static final String USER_PROFILE_KEY_PREFIX = "user:profile:";

    /**
     * 角色对应的权限集合 KEY 前缀
     */
    public static final String ROLE_PERMISSIONS_KEY_PREFIX = "role:permissions:";

    /**
     * 构建用户角色 KEY
     *
     * @param userId
     * @return
     */
    public static String buildUserRoleKey(Long userId) {
        return USER_ROLE_KEY_PREFIX + userId;
    }

    /**
     * 构建角色对应的权限集合 KEY
     *
     * @param userKey
     * @return
     */
    public static String buildRolePermissionsKey(String userKey) {
        return ROLE_PERMISSIONS_KEY_PREFIX + userKey;
    }

    /**
     * 构建角色对应的权限集合 KEY
     *
     * @param userId
     * @return
     */
    public static String buildUserInfoKey(Long userId) {
        return USER_INFO_KEY_PREFIX + userId;
    }

    /**
     * 构建角色主页信息对应的 KEY
     *
     * @param userId
     * @return
     */
    public static String buildUserProfileKey(Long userId) {
        return USER_PROFILE_KEY_PREFIX + userId;
    }

}
