package com.danby.happynode.count.biz.consumer;

import cn.hutool.core.collection.CollUtil;

import com.danby.happynode.count.biz.constant.MQConstants;
import com.danby.happynode.count.biz.domain.mapper.CommentDOMapper;
import com.danby.happynode.count.biz.model.dto.AggregationCountLikeUnlikeCommentMqDTO;
import com.danby.happynode.framework.common.util.JsonUtils;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RocketMQMessageListener(consumerGroup = "happynode_group_" + MQConstants.TOPIC_COUNT_COMMENT_LIKE_2_DB
        , topic = MQConstants.TOPIC_COUNT_COMMENT_LIKE_2_DB)
@Slf4j
public class CountCommentLike2DBConsumer implements RocketMQListener<String> {
    @Autowired
    private CommentDOMapper commentDOMapper;

    // 每秒创建 5000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(5000);


    @Override
    public void onMessage(String body) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();
        log.info("## 消费到了 MQ 【计数: 评论点赞数入库】, {}...", body);

        List<AggregationCountLikeUnlikeCommentMqDTO> countList = null;
        try {
            countList = JsonUtils.parseList(body, AggregationCountLikeUnlikeCommentMqDTO.class);
        } catch (Exception e) {
            log.error("## 解析 JSON 字符串异常", e);
        }

        if (CollUtil.isNotEmpty(countList)) {
            countList.forEach(count ->
                    commentDOMapper.updateLikeTotalByCommentId(count.getCommentId(), count.getCount()));
        }
    }
}
