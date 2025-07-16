package com.danby.happynode.comment.biz.service;

import com.danby.happynode.comment.biz.model.vo.*;
import com.danby.happynode.framework.common.response.PageResponse;
import com.danby.happynode.framework.common.response.Response;

public interface CommentService {
    /**
     * 发布评论
     * @param publishCommentReqVO
     * @return
     */
    Response<?> publishComment(PublishCommentReqVO publishCommentReqVO);

    /**
     * 评论列表分页查询
     * @param findCommentPageListReqVO
     * @return
     */
    PageResponse<FindCommentItemRespVO> findCommentPageList(FindCommentPageListReqVO findCommentPageListReqVO);

    /**
     * 二级评论分页查询
     *
     * @param findChildCommentPageListReqVO
     * @return
     */
    PageResponse<FindChildCommentItemRespVO> findChildCommentPageList(FindChildCommentPageListReqVO findChildCommentPageListReqVO);

    /**
     * 评论点赞
     * @param likeCommentReqVO
     * @return
     */
    Response<?> likeComment(LikeCommentReqVO likeCommentReqVO);

    /**
     * 取消评论点赞
     * @param unLikeCommentReqVO
     * @return
     */
    Response<?> unlikeComment(UnLikeCommentReqVO unLikeCommentReqVO);

    /**
     * 删除本地评论缓存
     * @param commentId
     */
    void deleteCommentLocalCache(Long commentId);

    /**
     * 删除评论
     * @param deleteCommentReqVO
     * @return
     */
    Response<?> deleteComment(DeleteCommentReqVO deleteCommentReqVO);
}
