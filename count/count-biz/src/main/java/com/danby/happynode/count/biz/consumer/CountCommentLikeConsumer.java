package com.danby.happynode.count.biz.consumer;

import com.danby.happynode.count.biz.constant.MQConstants;
import com.danby.happynode.count.biz.constant.RedisKeyConstants;
import com.danby.happynode.count.biz.enums.LikeUnlikeCommentTypeEnum;
import com.danby.happynode.count.biz.model.dto.AggregationCountLikeUnlikeCommentMqDTO;
import com.danby.happynode.count.biz.model.dto.CommentLikeUnLikeCommentMqDTO;
import com.danby.happynode.framework.common.util.JsonUtils;
import com.github.phantomthief.collection.BufferTrigger;
import com.google.common.collect.Lists;
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
@RocketMQMessageListener(consumerGroup = "happynode_group_count_" + MQConstants.TOPIC_COMMENT_LIKE_OR_UNLIKE,
        topic = MQConstants.TOPIC_COMMENT_LIKE_OR_UNLIKE
)
@Slf4j
public class CountCommentLikeConsumer implements RocketMQListener<String> {

    private BufferTrigger bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(1000) // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(1)) // 多久聚合一次
            .setConsumerEx(this::consumeMessage) // 设置消费者方法
            .build();

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(String body) {
        // 往 bufferTrigger 中添加元素
        bufferTrigger.enqueue(body);

    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【评论点赞数】聚合消息, size: {}", bodys.size());
        log.info("==> 【评论点赞数】聚合消息, {}", JsonUtils.toJsonString(bodys));
        List<CommentLikeUnLikeCommentMqDTO> dtoList = Lists.newArrayList();
        for (String body : bodys) {
            CommentLikeUnLikeCommentMqDTO dto = JsonUtils.parseObject(body, CommentLikeUnLikeCommentMqDTO.class);
            dtoList.add(dto);
        }
        // 按照评论id对dto进行分组
        Map<Long, List<CommentLikeUnLikeCommentMqDTO>> commentIdAndDtoMap = dtoList.stream()
                .collect(Collectors.groupingBy(CommentLikeUnLikeCommentMqDTO::getCommentId));
        // 按组汇总数据，统计出最终的计数
        // 最终操作的计数对象
        List<AggregationCountLikeUnlikeCommentMqDTO> aggregationCountLikeUnlikeCommentMqDTOS = Lists.newArrayList();
        for (Map.Entry<Long, List<CommentLikeUnLikeCommentMqDTO>> entry : commentIdAndDtoMap.entrySet()) {
            Long commentId = entry.getKey();
            List<CommentLikeUnLikeCommentMqDTO> dtos = entry.getValue();
            Integer count = 0;
            for (CommentLikeUnLikeCommentMqDTO dto : dtos) {
                Integer type = dto.getType();
                LikeUnlikeCommentTypeEnum likeUnlikeCommentTypeEnum = LikeUnlikeCommentTypeEnum.valueOf(type);
                if (Objects.isNull(likeUnlikeCommentTypeEnum)) continue;
                switch (likeUnlikeCommentTypeEnum) {
                    case LIKE -> count += 1;
                    case UNLIKE -> count -= 1;
                }
            }

            aggregationCountLikeUnlikeCommentMqDTOS.add(AggregationCountLikeUnlikeCommentMqDTO.builder()
                    .commentId(commentId)
                    .count(count)
                    .build());
        }
        log.info("## 【评论点赞数】聚合后的计数数据: {}", JsonUtils.toJsonString(aggregationCountLikeUnlikeCommentMqDTOS));
        // TODO:
        aggregationCountLikeUnlikeCommentMqDTOS.forEach(dto -> {
            Long commentId = dto.getCommentId();
            Integer count = dto.getCount();
            String countCommentKey = RedisKeyConstants.buildCountCommentKey(commentId);
            Boolean hasKey = redisTemplate.hasKey(countCommentKey);
            if (hasKey) {
                redisTemplate.opsForHash().increment(countCommentKey, RedisKeyConstants.FIELD_LIKE_TOTAL, count);
            }
        });

        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(aggregationCountLikeUnlikeCommentMqDTOS)).build();
        String destination = MQConstants.TOPIC_COUNT_COMMENT_LIKE_2_DB;

        rocketMQTemplate.asyncSend(destination, message, new SendCallback() {

            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数服务：评论点赞数写库】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数服务：评论点赞数写库】MQ 发送异常: ", throwable);
            }
        });

    }
}
