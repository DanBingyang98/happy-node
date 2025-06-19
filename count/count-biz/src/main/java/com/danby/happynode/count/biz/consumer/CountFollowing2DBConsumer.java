package com.danby.happynode.count.biz.consumer;

import com.alibaba.nacos.shaded.com.google.common.util.concurrent.RateLimiter;
import com.danby.happynode.count.biz.constant.MQConstants;
import com.danby.happynode.count.biz.domain.mapper.UserCountDOMapper;
import com.danby.happynode.count.biz.enums.FollowUnfollowTypeEnum;
import com.danby.happynode.count.biz.model.dto.CountFollowUnfollowMqDTO;
import com.danby.happynode.framework.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RocketMQMessageListener(consumerGroup = "happynode_group" + MQConstants.TOPIC_COUNT_FOLLOWING_2_DB, // Group 组
        topic = MQConstants.TOPIC_COUNT_FOLLOWING_2_DB // Topic 主题
)
@Slf4j
public class CountFollowing2DBConsumer implements RocketMQListener<String> {

    @Autowired
    private UserCountDOMapper userCountDOMapper;

    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @Override
    public void onMessage(String body) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();
        log.info("## 消费到了 MQ 【计数: 关注数入库】, {}...", body);
        CountFollowUnfollowMqDTO countFollowUnfollowMqDTO = JsonUtils.parseObject(body, CountFollowUnfollowMqDTO.class);
        // 原用户ID
        Long userId = countFollowUnfollowMqDTO.getUserId();
        // 操作类型：关注 or 取关
        Integer type = countFollowUnfollowMqDTO.getType();
        // 关注数：关注 +1， 取关 -1
        int count = Objects.equals(type, FollowUnfollowTypeEnum.FOLLOW.getCode()) ? 1 : -1;
        userCountDOMapper.insertOrUpdateFollowingTotalByUserId(count, userId);

    }
}
