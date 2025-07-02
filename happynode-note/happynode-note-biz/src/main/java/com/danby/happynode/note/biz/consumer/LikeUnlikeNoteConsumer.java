package com.danby.happynode.note.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.nacos.shaded.com.google.common.util.concurrent.RateLimiter;
import com.danby.happynode.framework.common.util.JsonUtils;
import com.danby.happynode.note.biz.constant.MQConstants;
import com.danby.happynode.note.biz.domain.dataobject.NoteLikeDO;
import com.danby.happynode.note.biz.domain.mapper.NoteLikeDOMapper;
import com.danby.happynode.note.biz.model.dto.LikeUnlikeNoteMqDTO;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component

@Slf4j
public class LikeUnlikeNoteConsumer {


    @Value("${rocketmq.name-server}")
    private String namesrvAddr;
    // 每秒创建 5000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @Autowired
    private NoteLikeDOMapper noteLikeDOMapper;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    private DefaultMQPushConsumer consumer;

    @Bean(name = "LikeUnlikeNoteConsumer")
    public DefaultMQPushConsumer mqPushConsumer() throws Exception {
        // Group
        String group = "happynode_group_" + MQConstants.TOPIC_LIKE_OR_UNLIKE;
        // 创建一个新的DefaultMQPushConsumer实例，并设置Consumer的Group名称
        consumer = new DefaultMQPushConsumer(group);
        // 设置 RocketMQ 的 NameServer 地址
        consumer.setNamesrvAddr(namesrvAddr);
        // 订阅指定的主题，并设置主题的订阅规则（"*" 表示订阅所有标签的消息）
        consumer.subscribe(MQConstants.TOPIC_LIKE_OR_UNLIKE, "*");
        // 设置消费者消费消息的起始位置，如果队列中没有消息，则从最新的消息开始消费。
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        // 设置消费模式为集群模式
        consumer.setMessageModel(MessageModel.CLUSTERING);
        // 设置批量消费消息的大小，默认是 32
        consumer.setConsumeMessageBatchMaxSize(30);
        // 设置消息的最大重试次数，默认是 16
        consumer.setMaxReconsumeTimes(3);
        // 设置拉取消息的间隔时间，默认是 1000ms
        consumer.setPullInterval(1000);
        // 注册消费者监听器
        consumer.registerMessageListener((MessageListenerOrderly) (messages, context) -> {
            log.info("==> 【笔记点赞、取消点赞】本批次消息大小: {}", messages.size());
            try {
                // 令牌桶流控, 以控制数据库能够承受的 QPS
                rateLimiter.acquire();
                // 幂等性: 通过联合唯一索引保证

                // 消息体 Json 字符串转 DTO
                List<LikeUnlikeNoteMqDTO> likeUnlikeNoteMqDTOS = Lists.newArrayList();
                messages.forEach(message ->
                        likeUnlikeNoteMqDTOS.add(JsonUtils.parseObject(new String(message.getBody()), LikeUnlikeNoteMqDTO.class)));
                // 1. 内存级操作合并
                // 按用户 ID 进行分组
                Map<Long, List<LikeUnlikeNoteMqDTO>> groupByUserIdMap = likeUnlikeNoteMqDTOS.stream()
                        .collect(Collectors.groupingBy(LikeUnlikeNoteMqDTO::getUserId));
                List<LikeUnlikeNoteMqDTO> finalOperations = groupByUserIdMap.values().stream()
                        .flatMap(likeUnlikeNoteMqDTOList -> {
                            Map<Long, List<LikeUnlikeNoteMqDTO>> groupByNoteIdMap =
                                    likeUnlikeNoteMqDTOList.stream().collect(Collectors.groupingBy(LikeUnlikeNoteMqDTO::getNoteId));
                            return groupByNoteIdMap.entrySet().stream()
                                    .filter(entry -> {
                                        List<LikeUnlikeNoteMqDTO> operations = entry.getValue();
                                        int size = operations.size();
                                        if (size % 2 == 0) // // 偶数次操作：最终状态抵消，无需写入
                                            return false;
                                        else // 奇数次操作：保留最后一次操作
                                            return true;
                                    })
                                    .map(entry -> {
                                        // 取最后一次操作（消息是有序的）
                                        return entry.getValue().getLast();
                                    });
                        }).toList();
                // 2. 批量写入数据库
                if (CollUtil.isNotEmpty(finalOperations)) {
                    // DTO 转 DO
                    List<NoteLikeDO> noteLikeDOS = finalOperations.stream()
                            .map(dto ->
                                    NoteLikeDO.builder()
                                            .userId(dto.getUserId())
                                            .noteId(dto.getNoteId())
                                            .createTime(dto.getCreateTime())
                                            .status(dto.getType())
                                            .build())
                            .toList();
                    noteLikeDOMapper.batchInsertOrUpdate(noteLikeDOS);
                }
                // 手动 ACK，告诉 RocketMQ 这批次消息消费成功
                return ConsumeOrderlyStatus.SUCCESS;
            } catch (Exception e) {
                log.error("", e);
                // 这样 RocketMQ 会暂停当前队列的消费一段时间，再重试
                return ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT;
            }
        });
        consumer.start();
        return consumer;
    }


    public void onMessage(Message message) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();
        // 幂等性: 通过联合唯一索引保证
        // 消息体
        String bodyJsonStr = new String(message.getBody());
        String tags = message.getTags();
        log.info("==> LikeUnlikeNoteConsumer 消费了消息 {}, tags: {}", bodyJsonStr, tags);
        // 根据 MQ 标签，判断操作类型
        if (Objects.equals(tags, MQConstants.TAG_LIKE)) { // 点赞笔记
            handleLikeNoteTagMessage(bodyJsonStr);
        } else if (Objects.equals(tags, MQConstants.TAG_UNLIKE)) { // 取消点赞笔记
            handleUnlikeNoteTagMessage(bodyJsonStr);
        }

    }

    /**
     * 笔记点赞
     *
     * @param bodyJsonStr
     */
    private void handleLikeNoteTagMessage(String bodyJsonStr) {
        LikeUnlikeNoteMqDTO likeNoteMqDTO = JsonUtils.parseObject(bodyJsonStr, LikeUnlikeNoteMqDTO.class);
        if (Objects.isNull(likeNoteMqDTO)) return;
        // 用户Id
        Long userId = likeNoteMqDTO.getUserId();
        // 点赞的笔记ID
        Long noteId = likeNoteMqDTO.getNoteId();
        // 操作类型
        Integer type = likeNoteMqDTO.getType();
        // 点赞时间
        LocalDateTime createTime = likeNoteMqDTO.getCreateTime();
        // 构建 DO 对象
        NoteLikeDO noteLikeDO = NoteLikeDO.builder()
                .userId(userId)
                .noteId(noteId)
                .createTime(createTime)
                .status(type)
                .build();
        // 添加或更新笔记点赞记录
        int count = noteLikeDOMapper.insertOrUpdate(noteLikeDO);
        // 发送计数 MQ
        if (count > 0) {
            org.springframework.messaging.Message<String> message = MessageBuilder.withPayload(bodyJsonStr).build();
            rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_NOTE_LIKE, message, new SendCallback() {

                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("==> 【计数: 笔记点赞】MQ 发送成功，SendResult: {}", sendResult);
                }

                @Override
                public void onException(Throwable throwable) {
                    log.error("==> 【计数: 笔记点赞】MQ 发送异常: ", throwable);
                }
            });
        }

    }


    /**
     * 笔记取消点赞
     *
     * @param bodyJsonStr
     */
    private void handleUnlikeNoteTagMessage(String bodyJsonStr) {
        LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO = JsonUtils.parseObject(bodyJsonStr, LikeUnlikeNoteMqDTO.class);
        if (Objects.isNull(likeUnlikeNoteMqDTO)) return;
        Long userId = likeUnlikeNoteMqDTO.getUserId();
        Long noteId = likeUnlikeNoteMqDTO.getNoteId();
        Integer type = likeUnlikeNoteMqDTO.getType();
        LocalDateTime createTime = likeUnlikeNoteMqDTO.getCreateTime();
        // 构建 DO 对象
        NoteLikeDO noteLikeDO = NoteLikeDO.builder()
                .userId(userId)
                .noteId(noteId)
                .createTime(createTime)
                .status(type)
                .build();
        // 取消点赞：记录更新
        int count = noteLikeDOMapper.update2UnlikeByUserIdAndNoteId(noteLikeDO);
        // 发送计数 MQ
        if (count > 0) {
            org.springframework.messaging.Message<String> message = MessageBuilder.withPayload(bodyJsonStr).build();
            rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_NOTE_LIKE, message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("==> 【计数: 笔记取消点赞】MQ 发送成功，SendResult: {}", sendResult);
                }

                @Override
                public void onException(Throwable throwable) {
                    log.error("==> 【计数: 笔记取消点赞】MQ 发送异常: ", throwable);
                }
            });
        }
    }
}
