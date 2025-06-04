package com.danby.happynode.auth.service;

import com.danby.happynode.auth.model.vo.user.UpdatePasswordReqVO;
import com.danby.happynode.auth.model.vo.user.UserLoginReqVO;
import com.danby.happynode.framework.common.response.Response;

public interface AuthService {
    /**
     * 登录与注册
     *
     * @param userLoginReqVO
     * @return
     */
    Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO);


    /**
     * 退出登录
     *
     * @return Response<?>
     */
    Response<?> logout();

    /**
     * 修改密码
     * @param updatePasswordReqVO
     * @return
     */
    Response<?> updatePassword(UpdatePasswordReqVO updatePasswordReqVO);
}
