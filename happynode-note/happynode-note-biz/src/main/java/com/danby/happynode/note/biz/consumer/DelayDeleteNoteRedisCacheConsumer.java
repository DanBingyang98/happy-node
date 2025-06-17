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
@Slf4j
@RocketMQMessageListener(consumerGroup = "happynode_group_" + MQConstants.TOPIC_DELAY_DELETE_NOTE_REDIS_CACHE,
        topic = MQConstants.TOPIC_DELAY_DELETE_NOTE_REDIS_CACHE
)
public class DelayDeleteNoteRedisCacheConsumer implements RocketMQListener<String> {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(String s) {
        Long noteId = Long.valueOf(s);
        log.info("## 延迟消息消费成功, noteId: {}", noteId);

        String redisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        redisTemplate.delete(redisKey);

    }
}
