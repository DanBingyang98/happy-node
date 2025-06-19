package com.danby.happynode.count.biz.consumer;

import com.danby.happynode.count.biz.constant.MQConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(consumerGroup = "happynode_group" + MQConstants.TOPIC_COUNT_FANS, // Group 组
        topic = MQConstants.TOPIC_COUNT_FANS // Topic 主题
)
@Slf4j
public class CountFansConsumer implements RocketMQListener<String> {
    @Override
    public void onMessage(String body) {
        log.info("## 消费到了 MQ 【计数: 粉丝数】, {}...", body);
    }
}
