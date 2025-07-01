package com.danby.happynode.comment.biz.retry;

import com.danby.happynode.comment.biz.model.dto.PublishCommentMqDTO;
import com.danby.happynode.framework.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class SendMQRetryHelper {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Autowired
    private RetryTemplate retryTemplate;

    @Retryable(retryFor = {Exception.class}, // 需要重试的异常类型
            maxAttempts = 3, // 重试次数
            backoff = @Backoff(delay = 1000, multiplier = 2)) // 每次重试的间隔时间
    public void send(String topic, PublishCommentMqDTO publishCommentMqDTO) {
        log.info("==> 开始异步发送 MQ, Topic: {}, publishCommentMqDTO: {}", topic, publishCommentMqDTO);
        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(publishCommentMqDTO)).build();
        // 同步发送 MQ
        // 同步发送 MQ 能显式抛出异常，从而被 Retry 重试框架捕获到，才能进行重试操作。
        rocketMQTemplate.syncSend(topic, message);
    }

    public void asyncSend(String topic, PublishCommentMqDTO publishCommentMqDTO) {
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(publishCommentMqDTO)).build();
        rocketMQTemplate.asyncSend(topic, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【评论发布】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【评论发布】MQ 发送异常: ", throwable);
                handleRetry(topic, message);
            }
        });
    }

    private void handleRetry(String topic, Message<String> message) {
        threadPoolTaskExecutor.submit(() -> {
            try {
                // 通过retryTemplate.execute()方法，执行重试逻辑
                retryTemplate.execute((RetryCallback<Void, RuntimeException>) context -> {
                    log.info("==> 开始重试 MQ 发送, 当前重试次数: {}, 时间: {}", context.getRetryCount() + 1, LocalDateTime.now());
                    // 同步发送MQ
                    rocketMQTemplate.syncSend(topic, message);
                    return null;
                });
            } catch (Exception e) {
                // 多此重试失败，则调用此方法
                fallback(e, topic, message.getPayload());
            }
        });

    }

    /**
     * 兜底方案: 将发送失败的 MQ 写入数据库，之后，通过定时任务扫表，将发送失败的 MQ 再次发送，最终发送成功后，将该记录物理删除
     */
    private void fallback(Exception e, String topic, String bodyJson) {
        log.error("==> 多次发送失败, 进入兜底方案, Topic: {}, bodyJson: {}", topic, bodyJson);

        // TODO:
    }


    /**
     * 异步发送 MQ 失败后的处理逻辑
     * 重试多次后依然失败，则调用此方法，
     * 可以在此方法中，将发送失败的 MQ 写库，
     * 以便后续通过定时任务再次发送，做到最终发送 MQ 成功。
     */
    @Recover
    public void asyncSendMessageFallback(Exception e, String topic, PublishCommentMqDTO publishCommentMqDTO) {
        log.error("==> 多次发送失败, 进入兜底方案, Topic: {}, publishCommentMqDTO: {}", topic, publishCommentMqDTO);
        // TODO 添加业务逻辑

    }

}
