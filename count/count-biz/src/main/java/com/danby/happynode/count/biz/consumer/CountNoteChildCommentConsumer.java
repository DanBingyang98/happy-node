package com.danby.happynode.count.biz.consumer;

import com.danby.happynode.count.biz.constant.MQConstants;
import com.danby.happynode.count.biz.constant.RedisKeyConstants;
import com.danby.happynode.count.biz.domain.mapper.CommentDOMapper;
import com.danby.happynode.count.biz.enums.CommentLevelEnum;
import com.danby.happynode.count.biz.model.dto.CountPublishCommentMqDTO;
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
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RocketMQMessageListener(
        consumerGroup = "happynode_group_" + MQConstants.TOPIC_COUNT_NOTE_COMMENT,
        topic = MQConstants.TOPIC_COUNT_NOTE_COMMENT
)
@Slf4j
public class CountNoteChildCommentConsumer implements RocketMQListener<String> {

    @Autowired
    private CommentDOMapper commentDOMapper;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    private BufferTrigger bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(1000)   // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(1)) // 多久聚合一次（1s 一次）
            .setConsumerEx(this::consumeMessage) // 设置消费者方法
            .build();
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(String body) {
        // 往 bufferTrigger 中添加元素
        bufferTrigger.enqueue(body);
    }

    private void consumeMessage(List<String> strings) {
        log.info("==> 【评论数量更新】MQ 聚合消息, size: {}", strings.size());
//        List<CountPublishCommentMqDTO> countPublishCommentMqDTOS = strings.stream().map(s -> JsonUtils.parseObject(s, CountPublishCommentMqDTO.class)).toList();
        List<CountPublishCommentMqDTO> countPublishCommentMqDTOS = Lists.newArrayList();
        for (String s : strings) {
            try {
                List<CountPublishCommentMqDTO> list = JsonUtils.parseList(s, CountPublishCommentMqDTO.class);
                countPublishCommentMqDTOS.addAll(list);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        // 过滤出二级评论，并按 parent_id 分组  parentId -> [ commentId, commentId,... ]
        Map<Long, List<CountPublishCommentMqDTO>> groupMap = countPublishCommentMqDTOS.stream()
                .filter(countPublishCommentMqDTO -> Objects.equals(countPublishCommentMqDTO.getLevel(), CommentLevelEnum.TWO.getCode()))
                .collect(Collectors.groupingBy(CountPublishCommentMqDTO::getParentId)); // 按 parent_id 分组
        if (groupMap.isEmpty()) return;
        groupMap.forEach((parentId, levelTwoCommentMqDTOS) -> {
                    int count = levelTwoCommentMqDTOS.size();
                    String key = RedisKeyConstants.buildCountCommentKey(parentId);
                    Boolean hasKey = redisTemplate.hasKey(key);
                    if (hasKey) {
                        redisTemplate.opsForHash().increment(key, RedisKeyConstants.FIELD_CHILD_COMMENT_TOTAL, count);
                    }

                    // 更新一级评论的下级评论总数，进行累加操作
                    commentDOMapper.updateChildCommentTotal(parentId, count);
                }
        );
        // 获取字典中所有评论 ID
        Set<Long> commentIds = groupMap.keySet();
        // 异步发送计数 MQ, 更新评论热度值
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(commentIds)).build();
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COMMENT_HEAT_UPDATE, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【评论热度值更新】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【评论热度值更新】MQ 发送异常: ", throwable);
            }
        });
    }
}
