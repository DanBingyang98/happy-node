package com.danby.happynode.comment.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.nacos.shaded.com.google.common.util.concurrent.RateLimiter;
import com.danby.happynode.comment.biz.constant.MQConstants;
import com.danby.happynode.comment.biz.constant.RedisKeyConstants;
import com.danby.happynode.comment.biz.domain.dataobject.CommentDO;
import com.danby.happynode.comment.biz.domain.mapper.CommentDOMapper;
import com.danby.happynode.comment.biz.domain.mapper.NoteCountDOMapper;
import com.danby.happynode.comment.biz.enums.CommentLevelEnum;
import com.danby.happynode.framework.common.util.JsonUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "happynode_group_" + MQConstants.TOPIC_DELETE_COMMENT,
        topic = MQConstants.TOPIC_DELETE_COMMENT)
public class DeleteCommentConsumer implements RocketMQListener<String> {
    // 每秒创建 1000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(1000);
    @Autowired
    private CommentDOMapper commentDOMapper;
    @Autowired
    private NoteCountDOMapper noteCountDOMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(String body) {
        rateLimiter.acquire();
        log.info("## 【删除评论 - 后续业务处理】消费者消费成功, body: {}", body);
        CommentDO commentDO = JsonUtils.parseObject(body, CommentDO.class);
        Integer level = commentDO.getLevel();
        CommentLevelEnum commentLevel = CommentLevelEnum.valueOf(level);
        switch (commentLevel) {
            case ONE -> handleLevelOneComment(commentDO);
            case TWO -> handleLevelTwoComment(commentDO);
        }

    }

    /**
     * 一级评论处理
     *
     * @param commentDO
     */
    private void handleLevelOneComment(CommentDO commentDO) {
        Long noteId = commentDO.getNoteId();
        Long commentId = commentDO.getId();
        // 1. 关联评论删除（一级评论下所有子评论，都需要删除）
        int count = commentDOMapper.deleteByParentId(commentId);
        // 2. 计数更新（笔记下总评论数）
        // 更新 Redis 缓存
        String redisKey = RedisKeyConstants.buildCountCommentTotalKey(noteId);
        Boolean hasKey = redisTemplate.hasKey(redisKey);
        if (hasKey && count > 0) {
            // 笔记评论总数 -1
            redisTemplate.opsForHash().increment(redisKey, RedisKeyConstants.FIELD_COMMENT_TOTAL, -(count + 1));
        }
        // 更新 t_note_count 计数表
        noteCountDOMapper.updateCommentTotalByNoteId(noteId, -(count + 1));
    }

    /**
     * 二级评论处理
     *
     * @param commentDO
     */
    private void handleLevelTwoComment(CommentDO commentDO) {
        Long commentId = commentDO.getId();
        // 1. 批量删除关联评论（递归查询回复评论，并批量删除）
        ArrayList<Long> commentIdList = Lists.newArrayList();
        recurrentGetReplyCommentId(commentId, commentIdList);
        int count = 0;
        if (CollUtil.isNotEmpty(commentIdList)) {
            count = commentDOMapper.deleteByIds(commentIdList);
        }

        // 2. 更新一级评论的计数
        Long parentId = commentDO.getParentId();
        String countCommentKey = RedisKeyConstants.buildCountCommentKey(parentId);
        Boolean hasKey = redisTemplate.hasKey(countCommentKey);
        if (hasKey && count > 0) {
            redisTemplate.opsForHash().increment(countCommentKey, RedisKeyConstants.FIELD_CHILD_COMMENT_TOTAL, -(count + 1));
        }

        // 3. 若是最早的发布的二级评论被删除，需要更新一级评论的 first_reply_comment_id
        // 查询一级评论
        CommentDO parentCommentDO = commentDOMapper.selectByPrimaryKey(parentId);
        Long firstReplyCommentId = parentCommentDO.getFirstReplyCommentId();
        // 若删除的是最早回复的二级评论
        if (Objects.equals(firstReplyCommentId, commentId)) {
            // 查询数据库，重新获取一级评论最早回复的评论
            CommentDO earliestCommentDO = commentDOMapper.selectEarliestByParentId(parentId);
            Long earliestCommentId = Objects.nonNull(earliestCommentDO) ? earliestCommentDO.getId() : null;
            commentDOMapper.updateFirstReplyCommentIdByPrimaryKey(earliestCommentId, parentId);
        }
        // 4. 重新计算一级评论的热度值
        // 复用CommentHeatUpdateConsumer，发送一条MQ
        HashSet<Long> commentIds = Sets.newHashSetWithExpectedSize(1);
        // 异步发送计数 MQ, 更新评论热度值
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(commentIds)).build();
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COMMENT_HEAT_UPDATE, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【评论热度值更新】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【评论热度值更新】MQ 发送异常: ", throwable);
            }
        });
    }

    private void recurrentGetReplyCommentId(Long commentId, List<Long> commentIds) {
        CommentDO commentDO = commentDOMapper.selectByReplyCommentId(commentId);
        if (Objects.isNull(commentDO)) return;
        Long replyCommentId = commentDO.getId();
        commentIds.add(replyCommentId);
        recurrentGetReplyCommentId(replyCommentId, commentIds);
    }
}
