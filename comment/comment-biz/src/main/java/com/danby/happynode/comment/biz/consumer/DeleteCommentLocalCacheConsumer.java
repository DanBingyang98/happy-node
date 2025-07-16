package com.danby.happynode.comment.biz.consumer;

import com.danby.happynode.comment.biz.constant.MQConstants;
import com.danby.happynode.comment.biz.service.CommentService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "happynode_group_" + MQConstants.TOPIC_DELETE_COMMENT_LOCAL_CACHE,
        topic = MQConstants.TOPIC_DELETE_COMMENT_LOCAL_CACHE)
public class DeleteCommentLocalCacheConsumer implements RocketMQListener<String> {

    @Autowired
    private CommentService commentService;

    @Override
    public void onMessage(String s) {
        Long commentId = Long.valueOf(s);
        log.info("## 消费者消费成功, commentId: {}", commentId);
        commentService.deleteCommentLocalCache(commentId);
    }
}
