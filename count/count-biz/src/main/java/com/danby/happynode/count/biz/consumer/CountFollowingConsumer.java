package com.danby.happynode.count.biz.consumer;

import com.danby.happynode.count.biz.constant.MQConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(consumerGroup = "happynode_group" + MQConstants.TOPIC_COUNT_FOLLOWING, // Group 组
        topic = MQConstants.TOPIC_COUNT_FOLLOWING // Topic 主题
)
@Slf4j
public class CountFollowingConsumer implements RocketMQListener<String> {
    @Override
    public void onMessage(String body) {
        log.info("## 消费到了 MQ 【计数: 关注数】, {}...", body);

    }
}
