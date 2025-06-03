package com.danby.happynode.user.biz.service;

import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.user.biz.model.vo.UpdateUserInfoReqVO;

public interface UserService {
    /**
     * 更新用户信息
     *
     * @param updateUserInfoReqVO
     * @return
     */
    Response<?> updateUserInfo(UpdateUserInfoReqVO updateUserInfoReqVO);
}
