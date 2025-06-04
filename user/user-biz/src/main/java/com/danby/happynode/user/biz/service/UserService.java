package com.danby.happynode.user.biz.service;

import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.user.dto.req.FindUserByPhoneReqDTO;
import com.danby.happynode.user.dto.req.RegisterUserReqDTO;
import com.danby.happynode.user.biz.model.vo.UpdateUserInfoReqVO;
import com.danby.happynode.user.dto.req.UpdateUserPasswordReqDTO;
import com.danby.happynode.user.dto.resp.FindUserByPhoneRespDTO;

public interface UserService {
    /**
     * 更新用户信息
     *
     * @param updateUserInfoReqVO
     * @return
     */
    Response<?> updateUserInfo(UpdateUserInfoReqVO updateUserInfoReqVO);

    /**
     * 用户注册
     *
     * @param registerUserReqDTO
     * @return
     */
    Response<Long> register(RegisterUserReqDTO registerUserReqDTO);

    /**
     * 根据手机号查询用户信息
     *
     * @param findUserByPhoneReqDTO
     * @return
     */
    Response<FindUserByPhoneRespDTO> findByPhone(FindUserByPhoneReqDTO findUserByPhoneReqDTO);

    /***
     * 更新用户的密码
     * @param updateUserPasswordReqDTO
     * @return
     */
    Response<?> updatePassword(UpdateUserPasswordReqDTO updateUserPasswordReqDTO);
}
