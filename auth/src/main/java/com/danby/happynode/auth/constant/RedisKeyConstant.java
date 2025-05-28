package com.danby.happynode.auth.constant;

public class RedisKeyConstant {
    /**
     * 验证码 KEY 前缀
     */
    private static final String VERIFICATION_CODE_KEY_PREFIX = "verification_code:";

    /**
     * 构建验证码 KEY
     *
     * @param phone
     * @return
     */
    public static String buildVerificationCodeKey(String phone) {
        return VERIFICATION_CODE_KEY_PREFIX + phone;
    }

    /**
     * HappyNode 全局 ID 生成器 KEY
     */
    public static final String HAPPYNODE_ID_GENERATOR_KEY = "happynode.id.generator";

    /**
     * 用户角色 KEY 前缀
     */
    public static final String USER_ROLE_KEY_PREFIX = "user:roles:";

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
     * 角色对应的权限集合 KEY 前缀
     */
    public static final String ROLE_PERMISSIONS_KEY_PREFIX = "role:permissions:";

    /**
     * 构建角色对应的权限集合 KEY
     *
     * @param userKey
     * @return
     */
    public static String buildRolePermissionsKey(String userKey) {
        return ROLE_PERMISSIONS_KEY_PREFIX + userKey;
    }

}
