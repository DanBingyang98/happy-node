package com.danby.happynode.user.biz.rpc;

import com.danby.happynode.count.api.CountFeign;
import com.danby.happynode.count.dto.FindUserCountsByIdReqDTO;
import com.danby.happynode.count.dto.FindUserCountsByIdRespDTO;
import com.danby.happynode.framework.common.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CountRpcService {
    @Autowired
    CountFeign countFeign;

    public FindUserCountsByIdRespDTO findUserCountData(Long userId) {
        FindUserCountsByIdReqDTO findUserCountsByIdReqDTO = FindUserCountsByIdReqDTO.builder().userId(userId).build();
        Response<FindUserCountsByIdRespDTO> response = countFeign.findUserCountData(findUserCountsByIdReqDTO);
        if (response.isSuccess()) {
            return response.getData();
        }
        return null;


    }
}
