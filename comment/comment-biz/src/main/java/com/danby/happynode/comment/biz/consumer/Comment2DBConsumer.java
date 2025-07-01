package com.danby.happynode.comment.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.nacos.shaded.com.google.common.util.concurrent.RateLimiter;
import com.danby.happynode.comment.biz.constant.MQConstants;
import com.danby.happynode.comment.biz.domain.dataobject.CommentBO;
import com.danby.happynode.comment.biz.domain.dataobject.CommentDO;
import com.danby.happynode.comment.biz.domain.mapper.CommentDOMapper;
import com.danby.happynode.comment.biz.enums.CommentLevelEnum;
import com.danby.happynode.comment.biz.model.dto.PublishCommentMqDTO;
import com.danby.happynode.comment.biz.service.impl.CommentServiceImpl;
import com.danby.happynode.framework.common.util.JsonUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class Comment2DBConsumer {

    @Value("${rocketmq.name-server}")
    private String namesrvAddr;

    private DefaultMQPushConsumer consumer;

    @Autowired
    private CommentDOMapper commentDOMapper;

    // 每秒创建 1000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(1000);

    @Bean
    public DefaultMQPushConsumer mqPushConsumer(CommentServiceImpl commentServiceImpl) throws Exception {
        // Group组
        String group = "happynode_group_" + MQConstants.TOPIC_PUBLISH_COMMENT;
        // 创建一个新的DefaultMQPushConsumer实例，并设置Consumer的Group名称
        consumer = new DefaultMQPushConsumer(group);
        // 设置Consumer连接的NameServer地址
        consumer.setNamesrvAddr(namesrvAddr);
        // 设置Consumer订阅的Topic和Tag
        consumer.subscribe(MQConstants.TOPIC_PUBLISH_COMMENT, "*");
        // 设置Consumer从哪开始消费
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        // 设置Consumer的消费模式为 集群模式 (CLUSTERING)
        consumer.setMessageModel(MessageModel.CLUSTERING);
        // 设置Consumer一次最多消费30条消息
        consumer.setConsumeMessageBatchMaxSize(30);
        // 注册消费者监听器
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            log.info("==> 本批次消息大小: {}", msgs.size());
            try {
                // 令牌桶控流
                rateLimiter.acquire();
                List<PublishCommentMqDTO> publishCommentMqDTOS = new ArrayList<>();
                for (MessageExt msg : msgs) {
                    String message = new String(msg.getBody());
                    log.info("==> Consumer - Received message: {}", message);
                    String jsonStr = new String(msg.getBody());
                    publishCommentMqDTOS.add(JsonUtils.parseObject(jsonStr, PublishCommentMqDTO.class));
                }
                // 提取所有不为空的回复评论 ID
                List<Long> replyCommentIds = publishCommentMqDTOS.stream()
                        .filter(publishCommentMqDTO -> Objects.nonNull(publishCommentMqDTO.getReplyCommentId()))
                        .map(PublishCommentMqDTO::getReplyCommentId).toList();

                // 根据回复评论 ID 批量查询回复评论
                List<CommentDO> replyCommentDOs = null;
                if (CollUtil.isNotEmpty(replyCommentIds)) {
                    // 查询数据库
                    replyCommentDOs = commentDOMapper.selectByCommentIds(replyCommentIds);
                }
                // DO 集合转 <评论 ID - 评论 DO> 字典, 以方便后续查找
                Map<Long, CommentDO> commentIdAndCommentDOMap = Maps.newHashMap();
                if (CollUtil.isNotEmpty(replyCommentDOs)) {
                    commentIdAndCommentDOMap = replyCommentDOs.stream().collect(Collectors.toMap(CommentDO::getId, commentDO -> commentDO));
                }
                // DTO 转 BO
                List<CommentBO> commentBOS = Lists.newArrayList();
                for (PublishCommentMqDTO publishCommentMqDTO : publishCommentMqDTOS) {
                    String imageUrl = publishCommentMqDTO.getImageUrl();
                    CommentBO commentBO = CommentBO.builder()
                            .id(publishCommentMqDTO.getCommentId())
                            .noteId(publishCommentMqDTO.getNoteId())
                            .userId(publishCommentMqDTO.getCreatorId())
                            .isContentEmpty(true) // 默认评论内容为空
                            .imageUrl(StringUtils.isBlank(imageUrl) ? "" : imageUrl)
                            .level(CommentLevelEnum.ONE.getCode()) // 默认一级评论
                            .parentId(publishCommentMqDTO.getCommentId()) // 默认父级评论 ID
                            .createTime(publishCommentMqDTO.getCreateTime())
                            .updateTime(publishCommentMqDTO.getCreateTime())
                            .isTop(false)
                            .replyTotal(0L)
                            .likeTotal(0L)
                            .replyCommentId(0L)
                            .replyCommentId(0L)
                            .build();

                    // 评论内容若不为空
                    String content = publishCommentMqDTO.getContent();
                    if (StringUtils.isNotBlank(content)) {
                        commentBO.setContent(content);
                        commentBO.setContentUuid(UUID.randomUUID().toString());  // 生成评论内容的 UUID 标识
                        commentBO.setIsContentEmpty(false);
                    }
                    // 设置评论级别、回复用户 ID (reply_user_id)、父评论 ID (parent_id)
                    Long replyCommentId = publishCommentMqDTO.getReplyCommentId();
                    if (Objects.nonNull(replyCommentId)) {
                        CommentDO replyCommentDO = commentIdAndCommentDOMap.get(replyCommentId);
                        if (Objects.nonNull(replyCommentDO)) {
                            // 若回复的评论 ID 不为空，说明是二级评论
                            commentBO.setLevel(CommentLevelEnum.TWO.getCode());
                            commentBO.setReplyCommentId(publishCommentMqDTO.getReplyCommentId());
                            // 父评论ID
                            commentBO.setParentId(replyCommentDO.getId());
                            if (Objects.equals(replyCommentDO.getLevel(), CommentLevelEnum.TWO.getCode())) { // 如果回复的评论属于二级评论
                                commentBO.setParentId(replyCommentDO.getParentId());
                            }
                            // 回复的哪个用户
                            commentBO.setReplyUserId(replyCommentDO.getUserId());
                        }
                    }
                    commentBOS.add(commentBO);
                }
                log.info("## 清洗后的 CommentBOS: {}", JsonUtils.toJsonString(commentBOS));
                // TODO: 后续处理...

                //  手动 ACK，告诉 RocketMQ 这批次消息消费成功
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            } catch (Exception e) {
                log.error("", e);
                // 手动 ACK，告诉 RocketMQ 这批次消息处理失败，稍后再进行重试
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        });
        consumer.start();
        return consumer;
    }


    @PreDestroy
    public void destroy() {
        if (Objects.nonNull(consumer)) {
            try {
                consumer.shutdown();  // 关闭消费者
            } catch (Exception e) {
                log.error("", e);
            }
        }
    }

}
