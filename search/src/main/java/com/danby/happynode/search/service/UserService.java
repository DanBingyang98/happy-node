package com.danby.happynode.search.service;

import com.danby.happynode.framework.common.response.PageResponse;
import com.danby.happynode.search.model.vo.SearchUserReqVO;
import com.danby.happynode.search.model.vo.SearchUserRespVO;

public interface UserService {
    /**
     * 搜索用户
     * @param searchUserReqVO
     * @return
     */
    PageResponse<SearchUserRespVO> searchUser(SearchUserReqVO searchUserReqVO);
}
