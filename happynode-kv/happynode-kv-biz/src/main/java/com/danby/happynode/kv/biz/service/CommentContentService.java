package com.danby.happynode.kv.biz.service;

import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.kv.dto.req.BatchAddCommentContentReqDTO;
import com.danby.happynode.kv.dto.req.BatchFindCommentContentReqDTO;

public interface CommentContentService {
    /**
     * 批量添加评论内容
     *
     * @param batchAddCommentContentReqDTO
     * @return
     */
    Response<?> batchAddCommentContent(BatchAddCommentContentReqDTO batchAddCommentContentReqDTO);

    /**
     * 批量查询评论内容
     *
     * @param batchFindCommentContentReqDTO
     * @return
     */
    Response<?> batchFindCommentContent(BatchFindCommentContentReqDTO batchFindCommentContentReqDTO);
}
