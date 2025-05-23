package com.danby.happynode.auth.service;

import com.danby.happynode.auth.model.vo.user.UserLoginReqVO;
import com.danby.happynode.framework.common.response.Response;

public interface UserService {
    /**
     * 登录与注册
     * @param userLoginReqVO
     * @return
     */
    Response<String> loginAndRegister(UserLoginReqVO userLoginReqVO);
}
