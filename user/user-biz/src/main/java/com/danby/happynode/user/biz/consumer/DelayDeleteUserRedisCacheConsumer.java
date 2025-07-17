package com.danby.happynode.user.biz.consumer;

import com.danby.happynode.user.biz.constant.MQConstants;
import com.danby.happynode.user.biz.constant.RedisKeyConstant;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RocketMQMessageListener(consumerGroup = "happynode_group_" + MQConstants.TOPIC_DELAY_DELETE_USER_REDIS_CACHE,
        topic = MQConstants.TOPIC_DELAY_DELETE_USER_REDIS_CACHE)
@Slf4j
public class DelayDeleteUserRedisCacheConsumer implements RocketMQListener<String> {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(String s) {
        long userId = Long.parseLong(s);
        log.info("## 延迟消息消费成功, userId: {}", userId);
        // 删除user redis
        String userInfoKey = RedisKeyConstant.buildUserInfoKey(userId);
        String userProfileKey = RedisKeyConstant.buildUserProfileKey(userId);
        redisTemplate.delete(List.of(userInfoKey, userProfileKey));

    }
}
