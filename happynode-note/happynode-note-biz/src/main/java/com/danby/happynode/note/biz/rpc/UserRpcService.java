package com.danby.happynode.note.biz.rpc;

import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.user.api.UserServiceFeign;
import com.danby.happynode.user.dto.req.FindUserByIdReqDTO;
import com.danby.happynode.user.dto.resp.FindUserByIdRespDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class UserRpcService {
    @Autowired
    private UserServiceFeign userServiceFeign;

    /**
     * 查询用户信息
     *
     * @param userId
     * @return
     */
    public FindUserByIdRespDTO findById(Long userId) {
        FindUserByIdReqDTO findUserByIdReqDTO = new FindUserByIdReqDTO();
        findUserByIdReqDTO.setId(userId);
        Response<FindUserByIdRespDTO> response = userServiceFeign.findById(findUserByIdReqDTO);
        if (Objects.isNull(response) || !response.isSuccess()) {
            return null;
        }
        return response.getData();
    }
}
