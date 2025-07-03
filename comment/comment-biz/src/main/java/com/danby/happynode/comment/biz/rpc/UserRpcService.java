package com.danby.happynode.comment.biz.rpc;

import cn.hutool.core.collection.CollUtil;
import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.user.api.UserServiceFeign;
import com.danby.happynode.user.dto.req.FindUsersByIdsReqDTO;
import com.danby.happynode.user.dto.resp.FindUserByIdRespDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserRpcService {

    @Autowired
    private UserServiceFeign userServiceFeign;

    public List<FindUserByIdRespDTO> findUserByIds(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) return null;
        FindUsersByIdsReqDTO findUsersByIdsReqDTO = FindUsersByIdsReqDTO.builder()
                .ids(ids.stream().distinct().toList())
                .build();
        Response<List<FindUserByIdRespDTO>> response = userServiceFeign.findByIds(findUsersByIdsReqDTO);
        if (!response.isSuccess() || response.getData() == null || CollUtil.isEmpty(response.getData()))
            return null;
        return response.getData();

    }
}
