package com.danby.happynode.user.relation.biz.rpc;

import cn.hutool.core.collection.CollUtil;
import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.user.api.UserServiceFeign;
import com.danby.happynode.user.dto.req.FindUserByIdReqDTO;
import com.danby.happynode.user.dto.req.FindUsersByIdsReqDTO;
import com.danby.happynode.user.dto.resp.FindUserByIdRespDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserRpcService {
    @Autowired
    private UserServiceFeign userServiceFeign;

    public FindUserByIdRespDTO findById(Long userId) {
        FindUserByIdReqDTO findUserByIdReqDTO = FindUserByIdReqDTO.builder()
                .id(userId)
                .build();
        Response<FindUserByIdRespDTO> response = userServiceFeign.findById(findUserByIdReqDTO);
        if (!response.isSuccess() || response.getData() == null) {
            return null;
        }
        return response.getData();
    }

    public List<FindUserByIdRespDTO> findByIds(List<Long> userIds) {
        FindUsersByIdsReqDTO findUsersByIdsReqDTO = new FindUsersByIdsReqDTO(userIds);
        Response<List<FindUserByIdRespDTO>> response = userServiceFeign.findByIds(findUsersByIdsReqDTO);
        if (!response.isSuccess() || response.getData() == null || CollUtil.isEmpty(response.getData())) {
            return null;
        }
        return response.getData();
    }

}
