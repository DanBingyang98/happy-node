package com.danby.happynode.comment.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.danby.happynode.comment.biz.constant.MQConstants;
import com.danby.happynode.comment.biz.constant.RedisKeyConstants;
import com.danby.happynode.comment.biz.domain.dataobject.CommentDO;
import com.danby.happynode.comment.biz.domain.mapper.CommentDOMapper;
import com.danby.happynode.comment.biz.enums.CommentLevelEnum;
import com.danby.happynode.comment.biz.model.dto.CountPublishCommentMqDTO;
import com.danby.happynode.framework.common.util.JsonUtils;
import com.github.phantomthief.collection.BufferTrigger;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
@RocketMQMessageListener(
        consumerGroup = "happynode_group_first_reply_comment_id_" + MQConstants.TOPIC_COUNT_NOTE_COMMENT,
        topic = MQConstants.TOPIC_COUNT_NOTE_COMMENT
)
@Slf4j
public class OneLevelCommentFirstReplyCommentIdUpdateConsumer implements RocketMQListener<String> {

    private BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(1000)   // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(1)) // 多久聚合一次（1s 一次）
            .setConsumerEx(this::consumeMessage) // 设置消费者方法
            .build();

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private CommentDOMapper commentDOMapper;
    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Override
    public void onMessage(String body) {
        bufferTrigger.enqueue(body);
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【一级评论 first_reply_comment_id 更新】聚合消息, size: {}", bodys.size());
        log.info("==> 【一级评论 first_reply_comment_id 更新】聚合消息, {}", JsonUtils.toJsonString(bodys));
        List<CountPublishCommentMqDTO> countPublishCommentMqDTOList = Lists.newArrayList();
        for (String body : bodys) {
            try {
                List<CountPublishCommentMqDTO> list = JsonUtils.parseList(body, CountPublishCommentMqDTO.class);
                countPublishCommentMqDTOList.addAll(list);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        // 过滤出二级评论的 parent_id（即一级评论 ID），并去重，需要更新对应一级评论的 first_reply_comment_id
        List<Long> parentIds = countPublishCommentMqDTOList.stream()
                .filter(countPublishCommentMqDTO -> Objects.equals(countPublishCommentMqDTO.getLevel(), CommentLevelEnum.TWO.getCode()))
                .map(CountPublishCommentMqDTO::getParentId)
                .distinct()
                .toList();

        if (CollUtil.isEmpty(parentIds)) return;

        List<String> keys = parentIds.stream()
                .map(RedisKeyConstants::buildHaveFirstReplyCommentKey)
                .toList();
        // 批量查询 Redis
        List<Object> values = redisTemplate.opsForValue().multiGet(keys);
        List<Long> missingCommentIds = Lists.newArrayList();
        for (int i = 0; i < values.size(); i++) {
            if (Objects.isNull(values.get(i)))
                missingCommentIds.add(parentIds.get(i));
        }
        // 存在的一级评论 ID，说明表中对应记录的 first_reply_comment_id 已经有值
        if (CollUtil.isNotEmpty(missingCommentIds)) {
            // 不存在的，则需要进一步查询数据库来确定，是否要更新记录对应的 first_reply_comment_id 值
            // 批量去数据库中查询
            List<CommentDO> commentDOS = commentDOMapper.selectByCommentIds(missingCommentIds);
            // 异步将 first_reply_comment_id 不为 0 的一级评论 ID, 同步到 redis 中
            threadPoolTaskExecutor.submit(() -> {
                List<Long> needSyncCommentIds = commentDOS.stream()
                        .filter(commentDO -> commentDO.getFirstReplyCommentId() != 0)
                        .map(CommentDO::getId)
                        .toList();
                sync2Redis(needSyncCommentIds);
            });
            // 过滤出值为 0 的，都需要更新其 first_reply_comment_id
            List<CommentDO> needUpdateCommentDOS = commentDOS.stream()
                    .filter(commentDO -> commentDO.getFirstReplyCommentId() == 0)
                    .toList();
            needUpdateCommentDOS.forEach(needUpdateCommentDO -> {
                // 一级评论ID
                Long needUpdateCommentId = needUpdateCommentDO.getId();
                // 获取该一级评论下的最早的一条二级评论
                CommentDO commentDO = commentDOMapper.selectEarliestByParentId(needUpdateCommentId);
                if (Objects.nonNull(commentDO)) {
                    // 更新对应一级评论的 first_reply_comment_id
                    commentDOMapper.updateFirstReplyCommentIdByPrimaryKey(commentDO.getId(), needUpdateCommentId);
                    threadPoolTaskExecutor.submit(() -> sync2Redis(Lists.newArrayList(needUpdateCommentId)));
                }
            });
        }
    }

    private void sync2Redis(List<Long> needSyncCommentIds) {
        // 获取 ValueOperations
        ValueOperations<String, Object> stringObjectValueOperations = redisTemplate.opsForValue();
        // 使用 RedisTemplate 的管道模式，允许在一个操作中批量发送多个命令，防止频繁操作 Redis
        redisTemplate.executePipelined((RedisCallback<?>) (connection) -> {
            // 构建 Redis Key
            for (Long needSyncCommentId : needSyncCommentIds) {
                String key = RedisKeyConstants.buildHaveFirstReplyCommentKey(needSyncCommentId);
                stringObjectValueOperations.set(key, 1, RandomUtil.randomInt(5 * 60 * 60), TimeUnit.SECONDS);
            }
            return null;
        });

    }
}
