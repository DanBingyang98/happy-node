package com.danby.happynode.auth.domain.mapper;

import com.danby.happynode.auth.domain.dataobject.RoleDO;
import com.danby.happynode.auth.domain.dataobject.RolePermissionDO;

import java.util.List;

public interface RoleDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(RoleDO record);

    int insertSelective(RoleDO record);

    RoleDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(RoleDO record);

    int updateByPrimaryKey(RoleDO record);

    List<RoleDO> selectEnabledList();

}