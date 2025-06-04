package com.danby.happynode.auth.rpc;

import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.user.api.UserServiceFeign;
import com.danby.happynode.user.dto.req.FindUserByPhoneReqDTO;
import com.danby.happynode.user.dto.req.RegisterUserReqDTO;
import com.danby.happynode.user.dto.req.UpdateUserPasswordReqDTO;
import com.danby.happynode.user.dto.resp.FindUserByPhoneRespDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserRpcService {

    @Autowired
    private UserServiceFeign userServiceFeign;

    /**
     * 用户注册
     *
     * @param phone
     * @return
     */
    public Long registerUser(String phone) {
        RegisterUserReqDTO registerUserReqDTO = new RegisterUserReqDTO(phone);
        Response<Long> response = userServiceFeign.register(registerUserReqDTO);
        if (response.isSuccess()) {
            return response.getData();
        } else {
            return null;
        }
    }

    public FindUserByPhoneRespDTO findUserByPhone(String phone) {
        FindUserByPhoneReqDTO findUserByPhoneReqDTO = FindUserByPhoneReqDTO.builder().phone(phone).build();
        Response<FindUserByPhoneRespDTO> byPhone = userServiceFeign.findByPhone(findUserByPhoneReqDTO);
        if (byPhone.isSuccess()) {
            return byPhone.getData();
        } else {
            return null;
        }
    }

    public Response<?> updatePassword(String encodePassword) {
        UpdateUserPasswordReqDTO updateUserPasswordReqDTO = new UpdateUserPasswordReqDTO(encodePassword);
        return userServiceFeign.updatePassword(updateUserPasswordReqDTO);
    }
}
