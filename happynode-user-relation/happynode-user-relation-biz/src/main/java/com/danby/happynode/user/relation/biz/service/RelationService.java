package com.danby.happynode.user.relation.biz.service;

import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.user.relation.biz.model.vo.FollowUserReqVO;
import com.danby.happynode.user.relation.biz.model.vo.UnfollowUserReqVO;

public interface RelationService {
    /**
     * 关注用户
     *
     * @param followUserReqVO
     * @return
     */
    Response<?> follow(FollowUserReqVO followUserReqVO);

    /**
     * 取关用户
     *
     * @param unfollowUserReqVO
     * @return
     */
    Response<?> unfollow(UnfollowUserReqVO unfollowUserReqVO);
}
