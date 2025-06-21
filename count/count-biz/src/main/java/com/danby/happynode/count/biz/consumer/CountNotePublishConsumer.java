package com.danby.happynode.count.biz.consumer;

import com.danby.happynode.count.biz.constant.MQConstants;
import com.danby.happynode.count.biz.constant.RedisKeyConstants;
import com.danby.happynode.count.biz.domain.mapper.UserCountDOMapper;
import com.danby.happynode.count.biz.model.dto.NoteOperateMqDTO;
import com.danby.happynode.framework.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RocketMQMessageListener(
        consumerGroup = "happynode_group_" + MQConstants.TOPIC_NOTE_OPERATE, // group 组
        topic = MQConstants.TOPIC_NOTE_OPERATE
)
@Slf4j
public class CountNotePublishConsumer implements RocketMQListener<Message> {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserCountDOMapper userCountDOMapper;

    @Override
    public void onMessage(Message message) {
        // 消息体
        String bodyJsonStr = new String(message.getBody());
        // 消息标签
        String tags = message.getTags();
        log.info("==> CountNotePublishConsumer 消费了消息 {}, tags: {}", bodyJsonStr, tags);
        if (Objects.equals(tags, MQConstants.TAG_NOTE_PUBLISH)) {
            // 发布笔记
            handleNoteTagMessage(bodyJsonStr, 1);
        } else if (Objects.equals(tags, MQConstants.TAG_NOTE_DELETE)) {
            handleNoteTagMessage(bodyJsonStr, -1);
        }
    }

    /**
     * 笔记发布、删除
     *
     * @param bodyJsonStr
     */
    private void handleNoteTagMessage(String bodyJsonStr, Integer count) {
        // 消息体 JSON 字符串转 DTO
        NoteOperateMqDTO noteOperateMqDTO = JsonUtils.parseObject(bodyJsonStr, NoteOperateMqDTO.class);
        if (Objects.isNull(noteOperateMqDTO)) return;
        // 笔记发布者 ID
        Long creatorId = noteOperateMqDTO.getCreatorId();
        // 更新 Redis 中用户维度的计数 Hash
        String countUserRedisKey = RedisKeyConstants.buildCountUserKey(creatorId);
        // 判断 Redis 中 Hash 是否存在
        boolean isCountUserExisted = redisTemplate.hasKey(countUserRedisKey);
        // 若存在才会更新
        // (因为缓存设有过期时间，考虑到过期后，缓存会被删除，这里需要判断一下，存在才会去更新，而初始化工作放在查询计数来做)
        if (isCountUserExisted) {
            // 对目标用户 Hash 中的笔记发布总数，进行加减操作
            redisTemplate.opsForHash().increment(countUserRedisKey, RedisKeyConstants.FIELD_LIKE_TOTAL, count);
        }
        // 更新 t_user_count 表
        userCountDOMapper.insertOrUpdateLikeTotalByUserId(count, creatorId);
    }
}
