package com.danby.happynode.comment.biz.service;

import com.danby.happynode.comment.biz.model.vo.PublishCommentReqVO;
import com.danby.happynode.framework.common.response.Response;

public interface CommentService {
    /**
     * 发布评论
     * @param publishCommentReqVO
     * @return
     */
    Response<?> publishComment(PublishCommentReqVO publishCommentReqVO);
}
