package com.danby.happynode.count.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.nacos.shaded.com.google.common.util.concurrent.RateLimiter;
import com.danby.happynode.count.biz.constant.MQConstants;
import com.danby.happynode.count.biz.domain.mapper.NoteCountDOMapper;
import com.danby.happynode.framework.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RocketMQMessageListener(consumerGroup = "happynode_group_" + MQConstants.TOPIC_COUNT_NOTE_COLLECT_2_DB, // Group 组
        topic = MQConstants.TOPIC_COUNT_NOTE_COLLECT_2_DB // 主题 Topic
)
@Slf4j
public class CountNoteCollect2DBConsumer implements RocketMQListener<String> {

    @Autowired
    private NoteCountDOMapper noteCountDOMapper;

    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @Override
    public void onMessage(String s) {
        rateLimiter.acquire();
        log.info("## 消费到了 MQ 【计数: 笔记收藏数入库】, {}...", s);
        Map<Long, Integer> countMap = null;
        try {
            countMap = JsonUtils.parseMap(s, Long.class, Integer.class);
        } catch (Exception e) {
            log.error("## 解析 JSON 字符串异常", e);
        }
        if (CollUtil.isNotEmpty(countMap)) {
            // 判断数据库中 t_note_count 表，若笔记计数记录不存在，则插入；若记录已存在，则直接更新
            countMap.forEach((noteId, count) -> noteCountDOMapper.insertOrUpdateLikeTotalByNoteId(count, noteId));
        }
    }
}
