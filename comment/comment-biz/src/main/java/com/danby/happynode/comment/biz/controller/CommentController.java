package com.danby.happynode.comment.biz.controller;

import com.danby.happynode.comment.biz.model.vo.FindCommentItemRespVO;
import com.danby.happynode.comment.biz.model.vo.FindCommentPageListReqVO;
import com.danby.happynode.comment.biz.model.vo.PublishCommentReqVO;
import com.danby.happynode.comment.biz.service.CommentService;
import com.danby.happynode.framework.biz.operationlog.aspect.ApiOperationLog;
import com.danby.happynode.framework.common.response.PageResponse;
import com.danby.happynode.framework.common.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/comment")
public class CommentController {

    @Autowired
    private CommentService commentService;

    @RequestMapping("/publish")
    @ApiOperationLog(description = "发布评论")
    public Response<?> publishComment(@RequestBody @Validated PublishCommentReqVO publishCommentReqVO) {
        return commentService.publishComment(publishCommentReqVO);
    }

    @RequestMapping("/list")
    @ApiOperationLog(description = "获取评论列表")
    public PageResponse<FindCommentItemRespVO> findCommentPageList(@RequestBody @Validated FindCommentPageListReqVO findCommentPageListReqVO) {
        return commentService.findCommentPageList(findCommentPageListReqVO);
    }
}
