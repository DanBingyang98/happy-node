package com.danby.happynode.note.biz.consumer;

import com.danby.happynode.note.biz.constant.MQConstants;
import com.danby.happynode.note.biz.constant.RedisKeyConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(consumerGroup = "happynode_group_" + MQConstants.TOPIC_DELAY_DELETE_PUBLISHED_NOTE_LIST_REDIS_CACHE,
        topic = MQConstants.TOPIC_DELAY_DELETE_PUBLISHED_NOTE_LIST_REDIS_CACHE)
@Slf4j
public class DelayDeleteRedisPublishedNoteListConsumer implements RocketMQListener<String> {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(String body) {
        long creatorId = Long.parseLong(body);
        // 删除个人主页 - 已发布笔记列表缓存
        String publishedNoteListKey = RedisKeyConstants.buildPublishedNoteListKey(creatorId);
        // 批量删除
        redisTemplate.delete(publishedNoteListKey);
    }
}
