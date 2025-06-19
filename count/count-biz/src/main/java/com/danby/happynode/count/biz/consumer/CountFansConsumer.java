package com.danby.happynode.count.biz.consumer;

import com.danby.happynode.count.biz.constant.MQConstants;
import com.danby.happynode.count.biz.constant.RedisKeyConstants;
import com.danby.happynode.count.biz.enums.FollowUnfollowTypeEnum;
import com.danby.happynode.count.biz.model.dto.CountFollowUnfollowMqDTO;
import com.danby.happynode.framework.common.util.JsonUtils;
import com.github.phantomthief.collection.BufferTrigger;
import com.google.common.collect.Maps;
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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RocketMQMessageListener(consumerGroup = "happynode_group" + MQConstants.TOPIC_COUNT_FANS, // Group 组
        topic = MQConstants.TOPIC_COUNT_FANS // Topic 主题
)
@Slf4j
public class CountFansConsumer implements RocketMQListener<String> {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;
    private BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(1000)   // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(1)) // 多久聚合一次
            .setConsumerEx(this::consumeMessage)
            .build();

    @Override
    public void onMessage(String body) {
        log.info("## 消费到了 MQ 【计数: 粉丝数】, {}...", body);
        bufferTrigger.enqueue(body);
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 聚合消息, size: {}", bodys.size());
        log.info("==> 聚合消息, {}", JsonUtils.toJsonString(bodys));
        // List<String> 转 List<CountFollowUnfollowMqDTO>
        List<CountFollowUnfollowMqDTO> countFollowUnfollowMqDTOS = bodys.stream()
                .map(body -> JsonUtils.parseObject(body, CountFollowUnfollowMqDTO.class))
                .toList();
        // 根据 userId 分组
        Map<Long, List<CountFollowUnfollowMqDTO>> groupMap = countFollowUnfollowMqDTOS.stream()
                .collect(Collectors.groupingBy(CountFollowUnfollowMqDTO::getUserId));
        // 定义 userId 计数次数map
        Map<Long, Integer> countMap = Maps.newHashMap();
        for (Map.Entry<Long, List<CountFollowUnfollowMqDTO>> entry : groupMap.entrySet()) {
            // 用户id
            Long userId = entry.getKey();
            // 最终计数
            Integer finalCount = 0;
            List<CountFollowUnfollowMqDTO> value = entry.getValue();
            for (CountFollowUnfollowMqDTO countFollowUnfollowMqDTO : value) {
                Integer type = countFollowUnfollowMqDTO.getType();
                FollowUnfollowTypeEnum followUnfollowTypeEnum = FollowUnfollowTypeEnum.valueOf(type);
                if (Objects.isNull(followUnfollowTypeEnum)) continue;
                switch (followUnfollowTypeEnum) {
                    case FOLLOW -> finalCount += 1;
                    case UNFOLLOW -> finalCount -= 1;
                }
            }
            // 放入countMap中
            countMap.put(userId, finalCount);
        }
        log.info("## 聚合后的计数数据: {}", JsonUtils.toJsonString(countMap));
        countMap.forEach((userId, count) -> {
            // rediskey
            String redisKey = RedisKeyConstants.buildCountUserKey(userId);
            // 判断Redis中Hash是否存在
            Boolean isExisted = redisTemplate.hasKey(redisKey);
            // (因为缓存设有过期时间，考虑到过期后，缓存会被删除，这里需要判断一下，存在才会去更新，而初始化工作放在查询计数来做)
            if (isExisted) {
                // 存在则更新
                // 对目标用户 Hash 中的粉丝数字段进行计数操作
                redisTemplate.opsForHash().put(redisKey, RedisKeyConstants.FIELD_FANS_TOTAL, count);
            } else {
                // 不存在则新增
//                redisTemplate.opsForHash().put(redisKey, RedisKeyConstants.FIELD_FANS_TOTAL, count);
            }
        });
        // 发送 MQ, 计数数据落库
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(countMap)).build();
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_FANS_2_DB, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数服务：粉丝数入库】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数服务：粉丝数入库】MQ 发送异常: ", throwable);
            }
        });
    }
}
