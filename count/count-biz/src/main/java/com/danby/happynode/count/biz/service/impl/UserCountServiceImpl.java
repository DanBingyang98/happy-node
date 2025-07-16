package com.danby.happynode.count.biz.service.impl;

import com.danby.happynode.count.biz.domain.dataobject.UserCountDO;
import com.danby.happynode.count.biz.domain.mapper.UserCountDOMapper;
import com.danby.happynode.count.biz.service.UserCountService;
import com.danby.happynode.count.dto.FindUserCountsByIdReqDTO;
import com.danby.happynode.count.dto.FindUserCountsByIdRespDTO;
import com.danby.happynode.framework.common.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class UserCountServiceImpl implements UserCountService {

    @Autowired
    private UserCountDOMapper userCountDOMapper;

    @Override
    public Response<FindUserCountsByIdRespDTO> findUserCountData(FindUserCountsByIdReqDTO findUserCountsByIdReqDTO) {
        Long userId = findUserCountsByIdReqDTO.getUserId();
        FindUserCountsByIdRespDTO findUserCountsByIdRespDTO = FindUserCountsByIdRespDTO.builder()
                .userId(userId)
                .fansTotal(0L) // 相关计数默认值置为 0
                .noteTotal(0L)
                .followingTotal(0L)
                .likeTotal(0L)
                .collectTotal(0L)
                .build();
        UserCountDO userCountDO = userCountDOMapper.selectByUserId(userId);
        if (Objects.nonNull(userCountDO)) {
            findUserCountsByIdRespDTO.setCollectTotal(userCountDO.getCollectTotal());
            findUserCountsByIdRespDTO.setFansTotal(userCountDO.getFansTotal());
            findUserCountsByIdRespDTO.setNoteTotal(userCountDO.getNoteTotal());
            findUserCountsByIdRespDTO.setFollowingTotal(userCountDO.getFollowingTotal());
            findUserCountsByIdRespDTO.setLikeTotal(userCountDO.getLikeTotal());
        }
        return Response.success(findUserCountsByIdRespDTO);
    }
}
