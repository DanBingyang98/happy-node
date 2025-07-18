package com.danby.happynode.kv.biz.consumer;

import com.danby.happynode.framework.common.util.JsonUtils;
import com.danby.happynode.kv.biz.constant.MQConstants;
import com.danby.happynode.kv.biz.model.dto.PublishNoteDTO;
import com.danby.happynode.kv.biz.service.NoteContentService;
import com.danby.happynode.kv.dto.req.AddNoteContentReqDTO;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(consumerGroup = "happy_node_" + MQConstants.TOPIC_PUBLISH_NOTE_TRANSACTION,
        topic = MQConstants.TOPIC_PUBLISH_NOTE_TRANSACTION)
@Slf4j
public class SaveNoteContentConsumer implements RocketMQListener<Message> {

    @Autowired
    private NoteContentService noteContentService;

    @Override
    public void onMessage(Message s) {
        // 消息体
        String body = new String(s.getBody());
        log.info("## SaveNoteContentConsumer 消费了事务消息 {}", body);

        // 笔记正文保存到 Cassandra 中
        if (StringUtils.isNotBlank(body)) {
            PublishNoteDTO publishNoteDTO = JsonUtils.parseObject(body, PublishNoteDTO.class);
            String content = publishNoteDTO.getContent();
            String contentUuid = publishNoteDTO.getContentUuid();
            noteContentService.addNoteContent(AddNoteContentReqDTO.builder()
                    .content(content)
                    .uuid(contentUuid)
                    .build());
        }
    }
}
