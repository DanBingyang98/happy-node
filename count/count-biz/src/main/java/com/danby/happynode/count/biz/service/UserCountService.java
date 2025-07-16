package com.danby.happynode.count.biz.service;

import com.danby.happynode.count.dto.FindUserCountsByIdReqDTO;
import com.danby.happynode.count.dto.FindUserCountsByIdRespDTO;
import com.danby.happynode.framework.common.response.Response;

public interface UserCountService {
    /**
     * 查询用户相关计数
     * @param findUserCountsByIdReqDTO
     * @return
     */
    Response<FindUserCountsByIdRespDTO> findUserCountData(FindUserCountsByIdReqDTO findUserCountsByIdReqDTO);
}
