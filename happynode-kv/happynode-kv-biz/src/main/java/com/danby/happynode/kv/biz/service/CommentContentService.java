package com.danby.happynode.kv.biz.service;

import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.kv.dto.req.BatchAddCommentContentReqDTO;

public interface CommentContentService {
    /**
     * 批量添加评论内容
     *
     * @param batchAddCommentContentReqDTO
     * @return
     */
    Response<?> batchAddCommentContent(BatchAddCommentContentReqDTO batchAddCommentContentReqDTO);

}
