package com.danby.happynode.comment.biz.service.impl;

import com.danby.framework.context.holder.LoginUserContextHolder;
import com.danby.happynode.comment.biz.constant.MQConstants;
import com.danby.happynode.comment.biz.model.dto.PublishCommentMqDTO;
import com.danby.happynode.comment.biz.model.vo.PublishCommentReqVO;
import com.danby.happynode.comment.biz.retry.SendMQRetryHelper;
import com.danby.happynode.comment.biz.rpc.DistributedIdGeneratorRpcService;
import com.danby.happynode.comment.biz.service.CommentService;
import com.danby.happynode.framework.common.response.Response;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class CommentServiceImpl implements CommentService {

    @Autowired
    private SendMQRetryHelper sendMQRetryHelper;

    @Autowired
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;

    @Override
    public Response<?> publishComment(PublishCommentReqVO publishCommentReqVO) {
        // 评论正文
        String content = publishCommentReqVO.getContent();
        // 附近图片
        String imageUrl = publishCommentReqVO.getImageUrl();
        // 调用远程rpc服务，生成分布式commentId
        String commentId = distributedIdGeneratorRpcService.getGeneratedCommentId();

        // 评论内容和图片不能同时为空
        Preconditions.checkArgument(StringUtils.isNotBlank(content) || StringUtils.isNotBlank(imageUrl),
                "评论正文和图片不能同时为空");
        Long commentCreatorId = LoginUserContextHolder.getUserId();
        // 发送MQ消息

        // 1. 构建消息体 DTO
        PublishCommentMqDTO publishCommentMqDTO = PublishCommentMqDTO.builder()
                .replyCommentId(publishCommentReqVO.getReplyCommentId())  // 回复的哪个评论（评论 ID）
                .noteId(publishCommentReqVO.getNoteId()) // 所评论的笔记 ID
                .content(content) // 评论的文本内容
                .imageUrl(imageUrl) // 评论的图片url
                .createTime(LocalDateTime.now()) // 创建时间
                .creatorId(commentCreatorId) // 发布评论者的id
                .commentId(Long.valueOf(commentId)) // 评论id
                .build();
        // 2. 通过SendMQRetryHelper发送消息
//        sendMQRetryHelper.send(MQConstants.TOPIC_PUBLISH_COMMENT, publishCommentMqDTO);

        sendMQRetryHelper.asyncSend(MQConstants.TOPIC_PUBLISH_COMMENT, publishCommentMqDTO);

        return Response.success();
    }
}
