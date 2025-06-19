package com.danby.happynode.count.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.nacos.shaded.com.google.common.util.concurrent.RateLimiter;
import com.danby.happynode.count.biz.constant.MQConstants;
import com.danby.happynode.count.biz.domain.mapper.UserCountDOMapper;
import com.danby.happynode.framework.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RocketMQMessageListener(consumerGroup = "happynode_group" + MQConstants.TOPIC_COUNT_FANS_2_DB, // Group 组
        topic = MQConstants.TOPIC_COUNT_FANS_2_DB // Topic 主题
)
@Slf4j
public class CountFans2DBConsumer implements RocketMQListener<String> {

    @Autowired
    private UserCountDOMapper userCountDOMapper;
    // 每秒创建 5000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(5000);

    public CountFans2DBConsumer(UserCountDOMapper userCountDOMapper) {
        this.userCountDOMapper = userCountDOMapper;
    }

    @Override
    public void onMessage(String body) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();
        log.info("## 消费到了 MQ 【计数: 粉丝数入库】, {}...", body);
        Map<Long, Integer> countMap = null;
        try {
            countMap = JsonUtils.parseMap(body, Long.class, Integer.class);
        } catch (Exception e) {
            log.error("## 解析 JSON 字符串异常", e);
        }
        if (CollUtil.isNotEmpty(countMap)) {
            countMap.forEach((userId, count) -> userCountDOMapper.insertOrUpdateFansTotalByUserId(count, userId));
        }
    }
}
