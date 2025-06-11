package com.danby.happynode.user.api;

import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.user.constant.UserApiConstants;
import com.danby.happynode.user.dto.req.FindUserByIdReqDTO;
import com.danby.happynode.user.dto.req.FindUserByPhoneReqDTO;
import com.danby.happynode.user.dto.req.RegisterUserReqDTO;
import com.danby.happynode.user.dto.req.UpdateUserPasswordReqDTO;
import com.danby.happynode.user.dto.resp.FindUserByIdRespDTO;
import com.danby.happynode.user.dto.resp.FindUserByPhoneRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = UserApiConstants.SERVICE_NAME, path = UserApiConstants.SERVICE_PATH)
public interface UserServiceFeign {

    /**
     * 用户注册
     *
     * @param registerUserReqDTO
     * @return Response Long为userId
     */
    @PostMapping("/register")
    Response<Long> register(@Validated @RequestBody RegisterUserReqDTO registerUserReqDTO);

    /**
     * 根据手机号查询用户信息
     *
     * @param findUserByPhoneReqDTO
     * @return FindUserByPhoneReqDTO 封装了 id password
     */
    @PostMapping("/findByPhone")
    Response<FindUserByPhoneRespDTO> findByPhone(@Validated @RequestBody FindUserByPhoneReqDTO findUserByPhoneReqDTO);

    /***
     * 用户更新密码
     * @param updateUserPasswordReqDTO
     * @return Response.success()
     */
    @PostMapping("/updatePassword")
    Response<?> updatePassword(@Validated @RequestBody UpdateUserPasswordReqDTO updateUserPasswordReqDTO);

    /**
     * 根据用户 ID 查询用户信息
     *
     * @param findUserByIdReqDTO
     * @return
     */
    @PostMapping("/findById")
    Response<FindUserByIdRespDTO> findById(@Validated @RequestBody FindUserByIdReqDTO findUserByIdReqDTO);
}
