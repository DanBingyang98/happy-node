package com.danby.happynode.note.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.danby.framework.context.holder.LoginUserContextHolder;
import com.danby.happynode.framework.common.exception.BusinessException;
import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.framework.common.util.JsonUtils;
import com.danby.happynode.note.biz.constant.MQConstants;
import com.danby.happynode.note.biz.constant.RedisKeyConstants;
import com.danby.happynode.note.biz.domain.dataobject.NoteDO;
import com.danby.happynode.note.biz.domain.mapper.NoteDOMapper;
import com.danby.happynode.note.biz.domain.mapper.TopicDOMapper;
import com.danby.happynode.note.biz.enums.NoteStatusEnum;
import com.danby.happynode.note.biz.enums.NoteTypeEnum;
import com.danby.happynode.note.biz.enums.NoteVisibleEnum;
import com.danby.happynode.note.biz.enums.ResponseCodeEnum;
import com.danby.happynode.note.biz.model.vo.*;
import com.danby.happynode.note.biz.rpc.DistributedIdGeneratorRpcService;
import com.danby.happynode.note.biz.rpc.KeyValueRpcService;
import com.danby.happynode.note.biz.rpc.UserRpcService;
import com.danby.happynode.note.biz.service.NoteService;
import com.danby.happynode.user.dto.resp.FindUserByIdRespDTO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NoteServiceImpl implements NoteService {

    @Autowired
    private NoteDOMapper noteDOMapper;
    @Autowired
    private TopicDOMapper topicDOMapper;
    @Autowired
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Autowired
    private KeyValueRpcService keyValueRpcService;
    @Autowired
    private UserRpcService userRpcService;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    private static final Cache<Long, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(10000) // 设置初始容量为 10000 个条目
            .maximumSize(10000) // 设置缓存的最大容量为 10000 个条目
            .expireAfterWrite(1, TimeUnit.HOURS) // 设置缓存条目在写入后 1 小时过期
            .build();

    @Override
    public Response<?> publishNote(PublishNoteReqVO publishNoteReqVO) {
        // 笔记类型
        Integer type = publishNoteReqVO.getType();
        // 获取对应类型的枚举
        NoteTypeEnum noteTypeEnum = NoteTypeEnum.valueOf(type);
        // 若非图文、视频，抛出业务业务异常
        if (Objects.isNull(noteTypeEnum)) {
            throw new BusinessException(ResponseCodeEnum.NOTE_TYPE_ERROR);
        }
        String imgUris = null;
        // 笔记内容是否为空，默认值为 true，即空
        Boolean isContentEmpty = Boolean.FALSE;
        String videoUri = null;
        switch (noteTypeEnum) {
            case IMAGE_TEXT: // 图文笔记
                List<String> imgUriList = publishNoteReqVO.getImgUris();
                // 校验图片是否为空
                Preconditions.checkArgument(CollUtil.isNotEmpty(imgUriList), "笔记图片不能为空");
                // 校验图片数量
                Preconditions.checkArgument(imgUriList.size() <= 8, "笔记图片不能多于 8 张");
                // 将图片链接拼接，以逗号分隔
                imgUris = StringUtils.join(imgUriList, ",");
                break;
            case VIDEO: // 视频笔记
                videoUri = publishNoteReqVO.getVideoUri();
                // 校验视频链接是否为空
                Preconditions.checkArgument(StringUtils.isNotBlank(videoUri), "笔记视频不能为空");
                break;
            default:
                break;
        }
        // RPC: 调用分布式 ID 生成服务，生成笔记 ID
        String snowflakeIdId = distributedIdGeneratorRpcService.getSnowflakeId();
        String contentUuid = null;

        // 笔记内容
        String content = publishNoteReqVO.getContent();
        // 若用户填写了笔记内容
        if (StringUtils.isNotBlank(content)) {
            // 内容是否为空，置为 false，即不为空
            isContentEmpty = false;
            // 生成笔记内容 UUID
            contentUuid = UUID.randomUUID().toString();
            // RPC: 调用 KV 键值服务，存储短文本
            boolean isSavedSuccess = keyValueRpcService.addNoteContent(contentUuid, content);

            // 若存储失败，抛出业务异常，提示用户发布笔记失败
            if (!isSavedSuccess) {
                throw new BusinessException(ResponseCodeEnum.NOTE_PUBLISH_FAIL);
            }
        }

        // 话题
        Long topicId = publishNoteReqVO.getTopicId();
        String topicName = null;
        if (Objects.nonNull(topicId)) {
            // 获取话题名称
            topicName = topicDOMapper.selectNameByPrimaryKey(topicId);
        }

        // 发布者用户 ID
        Long creatorId = LoginUserContextHolder.getUserId();

        // 构建笔记 DO 对象
        NoteDO noteDO = NoteDO.builder()
                .id(Long.valueOf(snowflakeIdId))
                .isContentEmpty(isContentEmpty)
                .creatorId(creatorId)
                .imgUris(imgUris)
                .title(publishNoteReqVO.getTitle())
                .topicId(publishNoteReqVO.getTopicId())
                .topicName(topicName)
                .type(type)
                .visible(NoteVisibleEnum.PUBLIC.getCode())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .status(NoteStatusEnum.NORMAL.getCode())
                .isTop(Boolean.FALSE)
                .videoUri(videoUri)
                .contentUuid(contentUuid)
                .build();
        try {
            // 笔记入库存储
            noteDOMapper.insert(noteDO);
        } catch (Exception e) {
            log.error("==> 笔记存储失败", e);
            // RPC: 笔记保存失败，则删除笔记内容
            if (StringUtils.isNotBlank(contentUuid)) {
                keyValueRpcService.deleteNoteContent(contentUuid);
            }
        }
        return Response.success();
    }

    @Override
    @SneakyThrows
    public Response<FindNoteDetailRespVO> findNoteDetail(FindNoteDetailReqVO findNoteDetailReqVO) {
        Long noteId = findNoteDetailReqVO.getId();
        // 当前登录用户
        Long userId = LoginUserContextHolder.getUserId();
        // 先从本地缓存中查询
        String findNoteDetailRespVOStr = LOCAL_CACHE.getIfPresent(noteId);
        if (StringUtils.isNotBlank(findNoteDetailRespVOStr)) {
            FindNoteDetailRespVO findNoteDetailRespVO = JsonUtils.parseObject(findNoteDetailRespVOStr, FindNoteDetailRespVO.class);
            log.info("==> 命中了本地缓存；{}", findNoteDetailRespVOStr);
            // 校验笔记的可见性
            if (Objects.equals(findNoteDetailRespVO.getVisible(), NoteVisibleEnum.PRIVATE.getCode())
                    && !Objects.equals(findNoteDetailRespVO.getCreatorId(), userId)) {
                throw new BusinessException(ResponseCodeEnum.NOTE_PRIVATE);
            }
            return Response.success(findNoteDetailRespVO);
        }

        // 从redis缓存中获取
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        String noteDetailRedisValue = redisTemplate.opsForValue().get(noteDetailRedisKey);
        // 若缓存中有该笔记的数据，则直接返回
        if (StringUtils.isNotBlank(noteDetailRedisValue)) {
            FindNoteDetailRespVO findNoteDetailRespVO = JsonUtils.parseObject(noteDetailRedisValue, FindNoteDetailRespVO.class);
            // 异步线程中将用户信息存入本地缓存
            threadPoolTaskExecutor.submit(() -> {
                // 写入本地缓存
                LOCAL_CACHE.put(noteId, Objects.isNull(findNoteDetailRespVO) ? "null" : JsonUtils.toJsonString(findNoteDetailRespVO));
            });
            if (Objects.nonNull(findNoteDetailRespVO)) {
                // 校验笔记的可见性
                if (Objects.equals(findNoteDetailRespVO.getVisible(), NoteVisibleEnum.PRIVATE.getCode())
                        && !Objects.equals(findNoteDetailRespVO.getCreatorId(), userId)) {
                    throw new BusinessException(ResponseCodeEnum.NOTE_PRIVATE);
                }
                return Response.success(findNoteDetailRespVO);
            }
        }
        // 若redis没有缓存note detail 从数据库查找
        NoteDO noteDO = noteDOMapper.selectByPrimaryKey(noteId);
        if (Objects.isNull(noteDO)) {
            threadPoolTaskExecutor.execute(() -> {
                // 防止缓存穿透 将空数据存入redis
                // 保底1分钟 + 随机秒数
                long expireSeconds = 60 + RandomUtil.randomInt(60);
                redisTemplate.opsForValue().set(noteDetailRedisKey, "null", expireSeconds, TimeUnit.SECONDS);

            });
            throw new BusinessException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        // 校验笔记的可见性
        if (Objects.equals(noteDO.getVisible(), NoteVisibleEnum.PRIVATE.getCode())
                && !Objects.equals(noteDO.getCreatorId(), userId)) {
            throw new BusinessException(ResponseCodeEnum.NOTE_PRIVATE);
        }


        Long creatorId = noteDO.getCreatorId();
//        FindUserByIdRespDTO findUserByIdRespDTO = userRpcService.findById(creatorId);
        // RPC: 调用用户服务
        CompletableFuture<FindUserByIdRespDTO> userResultFuture = CompletableFuture.supplyAsync(
                () -> userRpcService.findById(creatorId), threadPoolTaskExecutor
        );

        // RPC: 调用 K-V 存储服务获取内容
        CompletableFuture<String> contentResultFuture = CompletableFuture.completedFuture(null);
        if (!noteDO.getIsContentEmpty()) {
            contentResultFuture = CompletableFuture.supplyAsync(() ->
                    keyValueRpcService.findNoteContent(noteDO.getContentUuid()), threadPoolTaskExecutor
            );
        }
        CompletableFuture<String> finalContentResultFuture = contentResultFuture;
        CompletableFuture<FindNoteDetailRespVO> findNoteDetailRespVOCompletableFuture = CompletableFuture.allOf(userResultFuture, contentResultFuture)
                .thenApply(s -> {
                    FindUserByIdRespDTO findUserByIdRespDTO = userResultFuture.join();
                    String content = finalContentResultFuture.join();
                    // 笔记类型
                    Integer type = noteDO.getType();
                    // 图文笔记图片链接(字符串)
                    String imgUrisStr = noteDO.getImgUris();
                    // 图文笔记图片链接(集合)
                    List<String> imgUris = null;
                    // 如果查询的是图文笔记，需要将图片链接的逗号分隔开，转换成集合
                    if (Objects.equals(type, NoteTypeEnum.IMAGE_TEXT.getCode()) && StringUtils.isNotBlank(imgUrisStr)) {
                        imgUris = Arrays.asList(imgUrisStr.split(","));
                    }
                    return FindNoteDetailRespVO.builder()
                            .id(noteId)
                            .type(type)
                            .title(noteDO.getTitle())
                            .content(content)
                            .imgUris(imgUris)
                            .topicId(noteDO.getTopicId())
                            .topicName(noteDO.getTopicName())
                            .creatorId(noteDO.getCreatorId())
                            .creatorName(findUserByIdRespDTO.getNickName())
                            .avatar(findUserByIdRespDTO.getAvatar())
                            .videoUri(noteDO.getVideoUri())
                            .updateTime(noteDO.getUpdateTime())
                            .visible(noteDO.getVisible())
                            .build();
                });

        FindNoteDetailRespVO findNoteDetailRespVO = findNoteDetailRespVOCompletableFuture.get();
        // 异步线程将笔记详情存入redis缓存
        threadPoolTaskExecutor.submit(() -> {
            long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
            String jsonString = JsonUtils.toJsonString(findNoteDetailRespVO);
            redisTemplate.opsForValue().set(noteDetailRedisKey, jsonString, expireSeconds, TimeUnit.SECONDS);
        });
        return Response.success(findNoteDetailRespVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<?> updateNote(UpdateNoteReqVO updateNoteReqVO) {
        // 笔记 ID
        Long noteId = updateNoteReqVO.getId();
        // 笔记类型
        Integer type = updateNoteReqVO.getType();

        // 获取对应类型的枚举
        NoteTypeEnum noteTypeEnum = NoteTypeEnum.valueOf(type);
        // 若非图文、视频，抛出业务业务异常
        if (Objects.isNull(noteTypeEnum)) {
            throw new BusinessException(ResponseCodeEnum.NOTE_TYPE_ERROR);
        }

        String imgUris = null;
        String videoUri = null;
        switch (noteTypeEnum) {
            case IMAGE_TEXT: // 图文笔记
                List<String> imgUriList = updateNoteReqVO.getImgUris();
                // 校验图片是否为空
                Preconditions.checkArgument(CollUtil.isNotEmpty(imgUriList), "笔记图片不能为空");
                // 校验图片数量
                Preconditions.checkArgument(imgUriList.size() <= 8, "笔记图片不能多于 8 张");
                imgUris = imgUriList.stream().collect(Collectors.joining(","));
                break;
            case VIDEO:
                videoUri = updateNoteReqVO.getVideoUri();
                Preconditions.checkArgument(StringUtils.isNotBlank(videoUri), "笔记视频不能为空");
                break;
            default:
                break;
        }
        // 话题
        Long topicId = updateNoteReqVO.getTopicId();
        String topicName = null;
        if (Objects.nonNull(topicId)) {
            topicName = topicDOMapper.selectNameByPrimaryKey(topicId);
            // 判断一下提交的话题, 是否是真实存在的
            if (StringUtils.isBlank(topicName)) {
                throw new BusinessException(ResponseCodeEnum.TOPIC_NOT_FOUND);
            }
        }

        // 删除 Redis 缓存
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        redisTemplate.delete(noteDetailRedisKey);


        String content = updateNoteReqVO.getContent();
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .isContentEmpty(StringUtils.isBlank(content))
                .imgUris(imgUris)
                .title(updateNoteReqVO.getTitle())
                .topicId(updateNoteReqVO.getTopicId())
                .topicName(topicName)
                .type(type)
                .updateTime(LocalDateTime.now())
                .videoUri(videoUri)
                .build();
        noteDOMapper.updateByPrimaryKey(noteDO);
        // 删除 Redis 缓存 一致性保证：延迟双删除
        // 异步发送延时消息
        Message<String> message = MessageBuilder.withPayload(String.valueOf(noteId))
                .build();
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_DELAY_DELETE_NOTE_REDIS_CACHE, message,
                new SendCallback() {

                    @Override
                    public void onSuccess(SendResult sendResult) {
                        log.info("## 延时删除 Redis 笔记缓存消息发送成功...");
                    }

                    @Override
                    public void onException(Throwable throwable) {
                        log.error("## 延时删除 Redis 笔记缓存消息发送失败...", throwable);
                    }
                },
                3000, //超时时间 毫秒,
                1 // 延迟级别，1 表示延迟 1s
        );


        // 删除本地缓存
//        LOCAL_CACHE.invalidate(noteId);
        // 同步发送广播模式 MQ，将所有实例中的本地缓存都删除掉
        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, noteId);
        log.info("====> MQ：删除笔记本地缓存发送成功...");

        NoteDO noteDO1 = noteDOMapper.selectByPrimaryKey(noteId);
        String contentUuid = noteDO1.getContentUuid();

        // 笔记内容是否更新成功
        boolean isUpdateContentSuccess = false;
        if (StringUtils.isBlank(content)) {
            // 若更新的笔记内容为空，则删除 K-V 存储
            isUpdateContentSuccess = keyValueRpcService.deleteNoteContent(contentUuid);
        } else {
            // 若将无内容的笔记，更新为了有内容的笔记，需要重新生成 UUID
            contentUuid = StringUtils.isBlank(contentUuid) ? UUID.randomUUID().toString() : contentUuid;
            // 调用 K-V 更新短文本
            isUpdateContentSuccess = keyValueRpcService.addNoteContent(contentUuid, content);
        }


        // 如果更新失败，抛出业务异常，回滚事务
        if (!isUpdateContentSuccess) {
            throw new BusinessException(ResponseCodeEnum.NOTE_UPDATE_FAIL);
        }

        return Response.success();
    }

    @Override
    public void deleteNoteLocalCache(Long noteId) {
        LOCAL_CACHE.invalidate(noteId);
    }

    @Override
    public Response<?> deleteNote(DeleteNoteReqVO deleteNoteReqVO) {
// 笔记 ID
        Long noteId = deleteNoteReqVO.getId();
        // 逻辑删除
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .status(NoteStatusEnum.DELETED.getCode())
                .updateTime(LocalDateTime.now())
                .build();

        int count = noteDOMapper.updateByPrimaryKeySelective(noteDO);
        // 若影响的行数为 0，则表示该笔记不存在
        if (count == 0) {
            throw new BusinessException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        // 删除缓存
        String redisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        redisTemplate.delete(redisKey);
        // 同步发送广播模式 MQ，将所有实例中的本地缓存都删除掉
        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, noteId);
        log.info("====> MQ：删除笔记本地缓存发送成功...");

        return Response.success();
    }

    @Override
    public Response<?> visibleOnlyMe(UpdateNoteVisibleOnlyMeReqVO updateNoteVisibleOnlyMeReqVO) {
        Long noteId = updateNoteVisibleOnlyMeReqVO.getId();
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .visible(NoteVisibleEnum.PRIVATE.getCode())
                .updateTime(LocalDateTime.now())
                .build();
        // 执行更新 SQL
        int count = noteDOMapper.updateByPrimaryKeySelective(noteDO);
        // 若影响的行数为 0，则表示该笔记无法修改为仅自己可见
        if (count == 0) {
            throw new BusinessException(ResponseCodeEnum.NOTE_CANT_VISIBLE_ONLY_ME);
        }
        // 删除 Redis 缓存
        redisTemplate.delete(RedisKeyConstants.buildNoteDetailKey(noteId));
        // 同步发送广播模式 MQ，将所有实例中的本地缓存都删除掉
        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, noteId);
        log.info("====> MQ：删除笔记本地缓存发送成功...");
        return Response.success();
    }

    @Override
    public Response<?> topNote(TopNoteReqVO topNoteReqVO) {
        Long noteId = topNoteReqVO.getId();
        Boolean isTop = topNoteReqVO.getIsTop();
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .isTop(isTop)
                .updateTime(LocalDateTime.now())
                .build();
        int count = noteDOMapper.updateIsTop(noteDO);
        if (count == 0) {
            throw new BusinessException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }
        redisTemplate.delete(RedisKeyConstants.buildNoteDetailKey(noteId));
        rocketMQTemplate.syncSend(MQConstants.TOPIC_DELETE_NOTE_LOCAL_CACHE, noteId);
        log.info("====> MQ：删除笔记本地缓存发送成功...");
        return Response.success();
    }
}
