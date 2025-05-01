package com.danby.happynode.auth.domain.mapper;

import com.danby.happynode.auth.domain.dataobject.UserDO;

public interface UserDOMapper {
    // 插入用户信息
    int insert(UserDO record);

    // 根据主键删除用户信息
    int deleteByPrimaryKey(Long id);

    // 根据主键更新用户信息
    int updateByPrimaryKey(UserDO record);

    // 根据主键查询用户信息
    UserDO selectByPrimaryKey(Long id);
}
