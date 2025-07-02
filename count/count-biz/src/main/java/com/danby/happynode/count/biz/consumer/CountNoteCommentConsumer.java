package com.danby.happynode.count.biz.consumer;

import com.danby.happynode.count.biz.constant.MQConstants;
import com.danby.happynode.count.biz.domain.mapper.NoteCountDOMapper;
import com.danby.happynode.count.biz.model.dto.CountPublishCommentMqDTO;
import com.danby.happynode.framework.common.util.JsonUtils;
import com.github.phantomthief.collection.BufferTrigger;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RocketMQMessageListener(
        consumerGroup = "happynode_group_" + MQConstants.TOPIC_COUNT_NOTE_COMMENT,
        topic = MQConstants.TOPIC_COUNT_NOTE_COMMENT
)
@Slf4j
public class CountNoteCommentConsumer implements RocketMQListener<String> {

    @Autowired
    private NoteCountDOMapper noteCountDOMapper;
    private BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(1000)   // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(1)) // 多久聚合一次（1s 一次）
            .setConsumerEx(this::consumeMessage) // 设置消费者方法
            .build();

    @Override
    public void onMessage(String body) {
        // 往 bufferTrigger 中添加元素
        bufferTrigger.enqueue(body);
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 【笔记评论数】聚合消息, size: {}", bodys.size());
        log.info("==> 【笔记评论数】聚合消息, {}", JsonUtils.toJsonString(bodys));
        // TODO:
//        List<CountPublishCommentMqDTO> countPublishCommentMqDTOS = bodys.stream().map(body -> JsonUtils.parseObject(body, CountPublishCommentMqDTO.class)).toList();
        List<CountPublishCommentMqDTO> countPublishCommentMqDTOS = Lists.newArrayList();
        for (String body : bodys) {
            try {
                List<CountPublishCommentMqDTO> list = JsonUtils.parseList(body, CountPublishCommentMqDTO.class);
                countPublishCommentMqDTOS.addAll(list);
            } catch (Exception e) {
                log.error("==> 【笔记评论数】聚合消息, 解析失败: {}", body, e);
            }
        }
        Map<Long, List<CountPublishCommentMqDTO>> groupMap = countPublishCommentMqDTOS.stream().collect(Collectors.groupingBy(CountPublishCommentMqDTO::getNoteId));
        groupMap.forEach((noteId, value) -> {
            int count = value.size();
            if (count > 0)
                noteCountDOMapper.insertOrUpdateCommentTotalByNoteId(count, noteId);
        });
    }
}
