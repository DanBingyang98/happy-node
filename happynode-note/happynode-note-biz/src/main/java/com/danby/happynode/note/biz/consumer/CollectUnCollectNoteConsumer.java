package com.danby.happynode.note.biz.consumer;

import com.alibaba.nacos.shaded.com.google.common.util.concurrent.RateLimiter;
import com.danby.happynode.framework.common.util.JsonUtils;
import com.danby.happynode.note.biz.constant.MQConstants;
import com.danby.happynode.note.biz.domain.dataobject.NoteCollectionDO;
import com.danby.happynode.note.biz.domain.mapper.NoteCollectionDOMapper;
import com.danby.happynode.note.biz.model.dto.CollectUnCollectNoteMqDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

@Component
@RocketMQMessageListener(
        consumerGroup = "happynode_group_" + MQConstants.TOPIC_COLLECT_OR_UN_COLLECT, // Group 组
        topic = MQConstants.TOPIC_COLLECT_OR_UN_COLLECT, // 消费的主题 Topic
        consumeMode = ConsumeMode.ORDERLY
)
@Slf4j
public class CollectUnCollectNoteConsumer implements RocketMQListener<Message> {

    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @Autowired
    private NoteCollectionDOMapper noteCollectionDOMapper;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(Message message) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();
        // 幂等性: 通过联合唯一索引保证
        // 消息体
        String bodyJsonStr = new String(message.getBody());
        // 标签
        String tags = message.getTags();
        log.info("==> CollectUnCollectNoteConsumer 消费了消息 {}, tags: {}", bodyJsonStr, tags);
        // 根据 MQ 标签，判断操作类型
        if (Objects.equals(tags, MQConstants.TAG_COLLECT)) { // 收藏操作
            handleCollectNoteTagMessage(bodyJsonStr);
        } else if (Objects.equals(tags, MQConstants.TAG_UN_COLLECT)) {
            handleUnCollectNoteTagMessage(bodyJsonStr);
        }
    }

    /**
     * 笔记收藏
     *
     * @param bodyJsonStr
     */
    private void handleCollectNoteTagMessage(String bodyJsonStr) {
        // 消息体 JSON 字符串转 DTO
        CollectUnCollectNoteMqDTO collectUnCollectNoteMqDTO = JsonUtils.parseObject(bodyJsonStr, CollectUnCollectNoteMqDTO.class);
        if (Objects.isNull(collectUnCollectNoteMqDTO)) return;
        // 用户ID
        Long userId = collectUnCollectNoteMqDTO.getUserId();
        // 收藏的笔记ID
        Long noteId = collectUnCollectNoteMqDTO.getNoteId();
        // 操作类型
        Integer type = collectUnCollectNoteMqDTO.getType();
        // 收藏时间
        LocalDateTime createTime = collectUnCollectNoteMqDTO.getCreateTime();
        // 构建 DO 对象
        NoteCollectionDO noteCollectionDO = NoteCollectionDO.builder()
                .userId(userId)
                .noteId(noteId)
                .createTime(createTime)
                .status(type)
                .build();
        // 添加或更新笔记收藏记录
        int count = noteCollectionDOMapper.insertOrUpdate(noteCollectionDO);
        // TODO: 发送计数 MQ
//        rocketMQTemplate
    }

    /**
     * 笔记取消收藏
     *
     * @param bodyJsonStr
     */
    private void handleUnCollectNoteTagMessage(String bodyJsonStr) {
        // 消息体 JSON 字符串转 DTO
        CollectUnCollectNoteMqDTO unCollectNoteMqDTO = JsonUtils.parseObject(bodyJsonStr, CollectUnCollectNoteMqDTO.class);
        if (Objects.isNull(unCollectNoteMqDTO)) return;
        // 用户ID
        Long userId = unCollectNoteMqDTO.getUserId();
        // 收藏的笔记ID
        Long noteId = unCollectNoteMqDTO.getNoteId();
        // 操作类型
        Integer type = unCollectNoteMqDTO.getType();
        // 收藏时间
        LocalDateTime createTime = unCollectNoteMqDTO.getCreateTime();
        // 构建 DO 对象
        NoteCollectionDO noteCollectionDO = NoteCollectionDO.builder()
                .userId(userId)
                .noteId(noteId)
                .createTime(createTime)
                .status(type)
                .build();
        // 取消收藏：记录更新
        int count = noteCollectionDOMapper.update2UnCollectByUserIdAndNoteId(noteCollectionDO);
        // TODO: 发送计数 MQ

    }
}
