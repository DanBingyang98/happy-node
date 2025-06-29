package com.danby.happynode.comment.biz.service.impl;

import com.danby.happynode.comment.biz.model.vo.PublishCommentReqVO;
import com.danby.happynode.comment.biz.service.CommentService;
import com.danby.happynode.framework.common.response.Response;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CommentServiceImpl implements CommentService {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public Response<?> publishComment(PublishCommentReqVO publishCommentReqVO) {
        // 评论正文
        String content = publishCommentReqVO.getContent();
        // 附近图片
        String imageUrl = publishCommentReqVO.getImageUrl();

        // 评论内容和图片不能同时为空
        Preconditions.checkArgument(StringUtils.isNotBlank(content) || StringUtils.isNotBlank(imageUrl),
                "评论正文和图片不能同时为空");

        // TODO

        return Response.success();
    }
}
