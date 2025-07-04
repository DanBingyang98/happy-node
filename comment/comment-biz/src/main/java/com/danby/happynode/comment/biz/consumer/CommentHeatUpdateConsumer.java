package com.danby.happynode.comment.biz.consumer;

import com.alibaba.nacos.shaded.com.google.common.collect.Sets;
import com.danby.happynode.comment.biz.constant.MQConstants;
import com.danby.happynode.comment.biz.constant.RedisKeyConstants;
import com.danby.happynode.comment.biz.domain.dataobject.CommentDO;
import com.danby.happynode.comment.biz.domain.mapper.CommentDOMapper;
import com.danby.happynode.comment.biz.model.bo.CommentHeatBO;
import com.danby.happynode.comment.biz.util.HeatCalculator;
import com.danby.happynode.framework.common.util.JsonUtils;
import com.github.phantomthief.collection.BufferTrigger;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RocketMQMessageListener(consumerGroup = "happynode_group_" + MQConstants.TOPIC_COMMENT_HEAT_UPDATE, // Group 组
        topic = MQConstants.TOPIC_COMMENT_HEAT_UPDATE // 主题 Topic
)
@Slf4j
public class CommentHeatUpdateConsumer implements RocketMQListener<String> {

    @Autowired
    private CommentDOMapper commentDOMapper;

    private BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(300)   // 一批次最多聚合 300 条
            .linger(Duration.ofSeconds(2)) // 多久聚合一次（2s 一次）
            .setConsumerEx(this::consumeMessage) // 设置消费者方法
            .build();
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(String body) {
        bufferTrigger.enqueue(body);
    }

    public void consumeMessage(List<String> bodys) {
        log.info("==> 【评论热度值计算】聚合消息, size: {}", bodys.size());
        log.info("==> 【评论热度值计算】聚合消息, {}", JsonUtils.toJsonString(bodys));
        // 将聚合后的消息体 Json 转 Set<Long>, 去重相同的评论 ID, 防止重复计算
        Set<Long> commentIds = Sets.newHashSet();
        bodys.forEach(body -> {
            try {
                Set<Long> list = JsonUtils.parseSet(body, Long.class);
                commentIds.addAll(list);
            } catch (Exception e) {
                log.error("", e);
            }
        });
        log.info("==> 去重后的评论 ID: {}", commentIds);

        // 批量查询评论
        List<CommentDO> commentDOS = commentDOMapper.selectByCommentIds(commentIds.stream().toList());
        // 评论id
        List<Long> ids = Lists.newArrayList();
        // 热度值BO
        List<CommentHeatBO> commentHeatBOS = Lists.newArrayList();
        //重新计算每条评论的热度值
        commentDOS.forEach(commentDO -> {
            Long commentId = commentDO.getId();
            // 被点赞数
            Long likeTotal = commentDO.getLikeTotal();
            // 被回复数
            Long childCommentTotal = commentDO.getChildCommentTotal();
            // 计算热度值
            BigDecimal heatNum = HeatCalculator.calculateHeat(likeTotal, childCommentTotal);
            ids.add(commentId);
            commentHeatBOS.add(CommentHeatBO.builder()
                    .id(commentId)
                    .heat(heatNum.doubleValue())
                    .noteId(commentDO.getNoteId())
                    .build());
        });
        // 批量更新评论热度值
        int count = commentDOMapper.batchUpdateHeatByCommentIds(ids, commentHeatBOS);
        if (count == 0) return;
        // 更新redis中的热度评论
        updateRedisHotComments(commentHeatBOS);
    }

    /**
     * 更新 Redis 中热点评论 ZSET
     *
     * @param commentHeatBOList
     */
    private void updateRedisHotComments(List<CommentHeatBO> commentHeatBOList) {
        // 过滤出热度值大于 0 的，并按所属笔记 ID 分组（若热度等于0，则不进行更新）
        Map<Long, List<CommentHeatBO>> noteIdAndBOListMap = commentHeatBOList.stream()
                .filter(commentHeatBO -> commentHeatBO.getHeat() > 0)
                .collect(Collectors.groupingBy(CommentHeatBO::getNoteId));
        // 循环
        noteIdAndBOListMap.forEach((noteId, commentHeatBOS) -> {
            String commentListKey = RedisKeyConstants.buildCommentListKey(noteId);
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            // Lua 脚本路径
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/update_hot_comments.lua")));
            // 返回值类型
            script.setResultType(Long.class);

            // 构建执行 Lua 脚本所需的 ARGS 参数
            List<Object> args = Lists.newArrayList();
            commentHeatBOS.forEach(commentHeatBO -> {
                args.add(commentHeatBO.getId()); // Member: 评论ID
                args.add(commentHeatBO.getHeat()); // Score: 热度值
            });
            redisTemplate.execute(script, Collections.singletonList(commentListKey), args.toArray());
        });
    }
}
