package com.danby.happynode.count.biz.consumer;

import com.danby.happynode.count.biz.constant.MQConstants;
import com.danby.happynode.count.biz.domain.mapper.CommentDOMapper;
import com.danby.happynode.count.biz.enums.CommentLevelEnum;
import com.danby.happynode.count.biz.model.dto.CountPublishCommentMqDTO;
import com.danby.happynode.framework.common.util.JsonUtils;
import com.github.phantomthief.collection.BufferTrigger;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private BufferTrigger bufferTrigger = BufferTrigger.<String>batchBlocking()
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

    private void consumeMessage(List<String> strings) {
        List<CountPublishCommentMqDTO> countPublishCommentMqDTOS = strings.stream().map(s -> JsonUtils.parseObject(s, CountPublishCommentMqDTO.class)).toList();
        // 过滤出二级评论，并按 parent_id 分组
        Map<Long, List<CountPublishCommentMqDTO>> groupMap = countPublishCommentMqDTOS.stream()
                .filter(countPublishCommentMqDTO -> Objects.equals(countPublishCommentMqDTO.getLevel(), CommentLevelEnum.TWO.getCode()))
                .collect(Collectors.groupingBy(CountPublishCommentMqDTO::getParentId)); // 按 parent_id 分组
        if (groupMap.isEmpty()) return;
        groupMap.forEach((parentId, levelTwoCommentMqDTOS) ->
                commentDOMapper.updateChildCommentTotal(parentId, levelTwoCommentMqDTOS.size())
        );
    }
}
