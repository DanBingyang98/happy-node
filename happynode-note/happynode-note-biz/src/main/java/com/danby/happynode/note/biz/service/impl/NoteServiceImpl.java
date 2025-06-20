package com.danby.happynode.note.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.danby.framework.context.holder.LoginUserContextHolder;
import com.danby.happynode.framework.common.exception.BusinessException;
import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.framework.common.util.DateUtils;
import com.danby.happynode.framework.common.util.JsonUtils;
import com.danby.happynode.note.biz.constant.MQConstants;
import com.danby.happynode.note.biz.constant.RedisKeyConstants;
import com.danby.happynode.note.biz.domain.dataobject.NoteDO;
import com.danby.happynode.note.biz.domain.dataobject.NoteLikeDO;
import com.danby.happynode.note.biz.domain.mapper.NoteDOMapper;
import com.danby.happynode.note.biz.domain.mapper.NoteLikeDOMapper;
import com.danby.happynode.note.biz.domain.mapper.TopicDOMapper;
import com.danby.happynode.note.biz.enums.*;
import com.danby.happynode.note.biz.model.dto.LikeUnlikeNoteMqDTO;
import com.danby.happynode.note.biz.model.vo.*;
import com.danby.happynode.note.biz.rpc.DistributedIdGeneratorRpcService;
import com.danby.happynode.note.biz.rpc.KeyValueRpcService;
import com.danby.happynode.note.biz.rpc.UserRpcService;
import com.danby.happynode.note.biz.service.NoteService;
import com.danby.happynode.user.dto.resp.FindUserByIdRespDTO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
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
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private NoteLikeDOMapper noteLikeDOMapper;

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
        String noteDetailRedisValue = (String) redisTemplate.opsForValue().get(noteDetailRedisKey);
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
        Long currUserId = LoginUserContextHolder.getUserId();
        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);
        if (Objects.isNull(selectNoteDO)) {
            throw new BusinessException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        // 判断权限：非笔记发布者不允许更新笔记
        if (!Objects.equals(currUserId, selectNoteDO.getCreatorId())) {
            throw new BusinessException(ResponseCodeEnum.NOTE_CANT_OPERATE);
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
        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);
        // 判断笔记是否存在
        if (Objects.isNull(selectNoteDO)) {
            throw new BusinessException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        // 判断权限：非笔记发布者不允许删除笔记
        Long currUserId = LoginUserContextHolder.getUserId();
        if (!Objects.equals(currUserId, selectNoteDO.getCreatorId())) {
            throw new BusinessException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }
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
        NoteDO selectNoteDO = noteDOMapper.selectByPrimaryKey(noteId);
        // 判断笔记是否存在
        if (Objects.isNull(selectNoteDO)) {
            throw new BusinessException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        // 判断权限：非笔记发布者不允许修改笔记权限
        Long currUserId = LoginUserContextHolder.getUserId();
        if (!Objects.equals(currUserId, selectNoteDO.getCreatorId())) {
            throw new BusinessException(ResponseCodeEnum.NOTE_CANT_OPERATE);
        }
        // 构建更新 DO 实体类
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

    @Override
    public Response<?> likeNote(LikeNoteReqVO likeNoteReqVO) {
        // 笔记ID
        Long noteId = likeNoteReqVO.getId();
        // 1. 校验被点赞的笔记是否存在
        checkNoteExist(noteId);
        // 2. 判断目标笔记，是否已经点赞过
        // 获取当前用户id
        Long userId = LoginUserContextHolder.getUserId();
        // 构建 Bloom 键
        String bloomUserNoteLikeListKey = RedisKeyConstants.buildBloomUserNoteLikeListKey(userId);
        // lua脚本
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // lua脚本路径 bloom_note_like_check.lua 布隆过滤器判断是否是未点赞
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_note_like_check.lua")));
        // lua脚本执行返回结果类型
        script.setResultType(Long.class);
        // 执行lua脚本 拿到返回结果
        Long result = redisTemplate.execute(script, Collections.singletonList(bloomUserNoteLikeListKey), noteId);
        // 根据脚本执行结果获取笔记点赞枚举类型
        NoteLikeLuaResultEnum noteLikeLuaResultEnum = NoteLikeLuaResultEnum.valueOf(result);
        // 用户点赞列表 ZSet Key
        String userNoteLikeZSetKey = RedisKeyConstants.buildUserNoteLikeZSetKey(userId);
        switch (noteLikeLuaResultEnum) {
            // Redis 中布隆过滤器不存在 表示未点赞
            case NOT_EXIST -> {
                // TODO: 从数据库中校验笔记是否被点赞，并异步初始化布隆过滤器，设置过期时间
                int count = noteLikeDOMapper.selectCountByUserIdAndNoteId(userId, noteId);
                // 保底1天+随机秒数
                long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
                if (count > 0) {
                    // 目标笔记已经被点赞
                    // 异步初始化布隆过滤器
                    threadPoolTaskExecutor.submit(() -> batchAddNoteLike2BloomAndExpire(userId, expireSeconds, bloomUserNoteLikeListKey));
                    throw new BusinessException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
                }
                // 若目标笔记未被点赞，查询当前用户是否有点赞其他笔记，有则同步初始化布隆过滤器
                batchAddNoteLike2BloomAndExpire(userId, expireSeconds, bloomUserNoteLikeListKey);
                DefaultRedisScript<Long> script1 = new DefaultRedisScript<>();
                // bloom_add_note_like_and_expire.lua 通过布隆过滤器判断是否已经点赞
                script1.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_add_note_like_and_expire.lua")));
                script1.setResultType(Long.class);
                redisTemplate.execute(script1, Collections.singletonList(bloomUserNoteLikeListKey), noteId, expireSeconds);

            }
            // 目标笔记已经被点赞 (可能存在误判，需要进一步确认)
            case NOTE_LIKED -> {
                // 校验 ZSet 列表中是否包含被点赞的笔记ID
                Double score = redisTemplate.opsForZSet().score(userNoteLikeZSetKey, noteId);
                if (Objects.nonNull(score)) {
                    //若 Score 不为空，若表示已点赞，抛出业务异常，提示用户 “已点赞”；
                    throw new BusinessException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
                } else {
                    // 若 Score 为空，则表示 ZSet 点赞列表中不存在，查询数据库校验
                    int count = noteLikeDOMapper.selectNoteIsLiked(userId, noteId);
                    if (count > 0) {
                        //若 count > 0 , 则表示数据库中存在目标笔记的点赞记录，抛出业务异常，提示用户 “已点赞”
                        //数据库里面有点赞记录，而 Redis 中 ZSet 不存在，需要重新异步初始化 ZSet
                        asynInitUserNoteLikesZSet(userId, userNoteLikeZSetKey);
                        throw new BusinessException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
                        //若数据库中不存在，才能继续执行后续的点赞流程
                    }
                }
            }
        }
        // 3. 更新用户 ZSET 点赞列表
        LocalDateTime now = LocalDateTime.now();
        script.setResultType(Long.class);
        // note_like_check_and_update_zset.lua 添加新的笔记点赞关系，如已经点赞了 100 篇笔记，则移除最早点赞的那篇
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/note_like_check_and_update_zset.lua")));
        result = redisTemplate.execute(script, Collections.singletonList(userNoteLikeZSetKey), noteId, DateUtils.localDateTime2Timestamp(now));
        // 若 ZSet 列表不存在，需要重新初始化
        if (Objects.equals(NoteLikeLuaResultEnum.NOT_EXIST.getCode(), result)) {
            // 查询当前用户最新点赞的 100 篇笔记
            List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectLikedByUserIdAndLimit(userId, 100);
            // 保底1天+随机秒数
            long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
            DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
            // batch_add_note_like_zset_and_expire.lua 批量添加点赞的笔记到 ZSet 中
            script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/batch_add_note_like_zset_and_expire.lua")));
            script2.setResultType(Long.class);
            // 若数据库中存在点赞记录，需要批量同步
            if (CollectionUtils.isNotEmpty(noteLikeDOS)) {
                // 构建Lua参数
                Object[] luaArgs = buildNoteLikeZSetLuaArgs(noteLikeDOS, expireSeconds);
                redisTemplate.execute(script2, Collections.singletonList(userNoteLikeZSetKey), luaArgs);
                // 再次调用 note_like_check_and_update_zset.lua 脚本，将点赞的笔记添加到 zset 中
                redisTemplate.execute(script, Collections.singletonList(userNoteLikeZSetKey), noteId, DateUtils.localDateTime2Timestamp(now));
            } else {// 若数据库中，无点赞过的笔记记录，则直接将当前点赞的笔记 ID 添加到 ZSet 中，随机过期时间
                ArrayList<Object> luaArgs = Lists.newArrayList();
                luaArgs.add(DateUtils.localDateTime2Timestamp(LocalDateTime.now())); // score ：点赞时间戳
                luaArgs.add(noteId);
                luaArgs.add(expireSeconds);
                redisTemplate.execute(script2, Collections.singletonList(userNoteLikeZSetKey), luaArgs.toArray());
            }
        }
        // 4. 发送 MQ, 将点赞数据落库
        // 构建消息体
        LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO = LikeUnlikeNoteMqDTO.builder()
                .noteId(noteId)
                .userId(userId)
                .type(LikeUnlikeNoteTypeEnum.LIKE.getCode()) // 点赞笔记
                .createTime(LocalDateTime.now())
                .build();
        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(likeUnlikeNoteMqDTO)).build();
        // 通过冒号连接, 可让 MQ 发送给主题 Topic 时，携带上标签 Tag
        String destination = MQConstants.TOPIC_LIKE_OR_UNLIKE + ":" + MQConstants.TAG_LIKE;
        String hashKey = String.valueOf(userId);
        rocketMQTemplate.asyncSendOrderly(destination, message, hashKey, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【笔记点赞】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【笔记点赞】MQ 发送异常: ", throwable);
            }
        });


        return Response.success();
    }

    @Override
    public Response<?> unlikeNote(UnlikeNoteReqVO unlikeNoteReqVO) {
        // 笔记ID
        Long noteId = unlikeNoteReqVO.getId();

        // 1. 校验笔记是否真实存在
        checkNoteExist(noteId);
        // TODO: 2. 校验笔记是否被点赞过
        // 用户id
        Long userId = LoginUserContextHolder.getUserId();
        // 布隆过滤器 Key
        String bloomUserNoteLikeListKey = RedisKeyConstants.buildBloomUserNoteLikeListKey(userId);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_note_unlike_check.lua")));
        script.setResultType(Long.class);
        Long luaResult = redisTemplate.execute(script, Collections.singletonList(bloomUserNoteLikeListKey), noteId);
        NoteUnlikeLuaResultEnum noteUnlikeLuaResultEnum = NoteUnlikeLuaResultEnum.valueOf(luaResult);
        switch (noteUnlikeLuaResultEnum) {
            case NOT_EXIST -> { // 布隆过滤器不存在
                // 异步初始化布隆过滤器
                threadPoolTaskExecutor.submit(() -> {
                    // 保底一天+随机秒数
                    long expireSecond = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
                    batchAddNoteLike2BloomAndExpire(userId, expireSecond, bloomUserNoteLikeListKey);
                });
                // 从数据库校验笔记是否已被点赞
                int count = noteLikeDOMapper.selectCountByUserIdAndNoteId(userId, noteId);
                // 未点赞，无法取消点赞操作，抛出业务异常
                if (count == 0) throw new BusinessException(ResponseCodeEnum.NOTE_NOT_LIKED);
            }
            case NOTE_NOT_LIKED -> throw new BusinessException(ResponseCodeEnum.NOTE_NOT_LIKED);
        }
        // 3. 删除 ZSET 中已点赞的笔记 ID
        String userNoteLikeZSetKey = RedisKeyConstants.buildUserNoteLikeZSetKey(userId);
        redisTemplate.opsForZSet().remove(userNoteLikeZSetKey, noteId);
        // TODO: 4. 发送 MQ, 数据更新落库

        return Response.success();
    }

    /**
     * 异步初始化布隆过滤器
     *
     * @param userId
     * @param expireSeconds
     * @param bloomUserNoteLikeListKey
     */
    private void batchAddNoteLike2BloomAndExpire(Long userId, long expireSeconds, String bloomUserNoteLikeListKey) {
        threadPoolTaskExecutor.submit(() -> {
            try {
                // 异步全量同步一下，并设置过期时间
                List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectByUserId(userId);
                if (CollUtil.isNotEmpty(noteLikeDOS)) {
                    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                    // Lua 脚本路径
                    script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_batch_add_note_like_and_expire.lua")));
                    // 返回值类型
                    script.setResultType(Long.class);
                    // 构建 Lua 参数
                    List<Object> luaArgs = Lists.newArrayList();
                    noteLikeDOS.forEach(noteLikeDO -> luaArgs.add(noteLikeDO.getNoteId())); // 将每个点赞的笔记 ID 传入
                    luaArgs.add(expireSeconds);  // 最后一个参数是过期时间（秒）
                    redisTemplate.execute(script, Collections.singletonList(bloomUserNoteLikeListKey), luaArgs.toArray());
                }
            } catch (Exception e) {
                log.error("## 异步初始化布隆过滤器异常: ", e);
            }
        });
    }

    private void checkNoteExist(Long noteId) {
        // 先从本地缓存校验
        String findNoteDetailRspVOStrLocalCache = LOCAL_CACHE.getIfPresent(noteId);
        FindNoteDetailRespVO findNoteDetailRespVO = JsonUtils.parseObject(findNoteDetailRspVOStrLocalCache, FindNoteDetailRespVO.class);
        if (Objects.isNull(findNoteDetailRspVOStrLocalCache)) {
            // 若本地缓存中没又该数据，则从 Redis 中查询
            String noteDetailKey = RedisKeyConstants.buildNoteDetailKey(noteId);
            String noteDetailValue = (String) redisTemplate.opsForValue().get(noteDetailKey);
            // 解析Json 字符串为对象
            findNoteDetailRespVO = JsonUtils.parseObject(noteDetailValue, FindNoteDetailRespVO.class);
            if (Objects.isNull(findNoteDetailRespVO)) {
                // 若 Redis 中没有该数据，则从数据库中查询
                int count = noteDOMapper.selectCountByNoteId(noteId);
                // 若数据库中也不存在，提示用户
                if (count == 0) {
                    throw new BusinessException(ResponseCodeEnum.NOTE_NOT_FOUND);
                }
                // 数据库存在，异步缓存
                threadPoolTaskExecutor.submit(() -> {
                    FindNoteDetailReqVO findNoteDetailReqVO = FindNoteDetailReqVO.builder().id(noteId).build();
                    findNoteDetail(findNoteDetailReqVO);
                });
            }
        }
    }

    /**
     * 构建 Lua 脚本参数
     *
     * @param noteLikeDOS
     * @param expireSeconds
     * @return
     */
    private static Object[] buildNoteLikeZSetLuaArgs(List<NoteLikeDO> noteLikeDOS, long expireSeconds) {
        int argsLength = noteLikeDOS.size() * 2 + 1; // 每个笔记点赞关系有 2 个参数（score 和 value），最后再跟一个过期时间
        Object[] luaArgs = new Object[argsLength];

        int i = 0;
        for (NoteLikeDO noteLikeDO : noteLikeDOS) {
            luaArgs[i] = DateUtils.localDateTime2Timestamp(noteLikeDO.getCreateTime()); // 点赞时间作为 score
            luaArgs[i + 1] = noteLikeDO.getNoteId();          // 笔记ID 作为 ZSet value
            i += 2;
        }

        luaArgs[argsLength - 1] = expireSeconds; // 最后一个参数是 ZSet 的过期时间
        return luaArgs;
    }

    /**
     * 异步初始化用户点赞笔记 ZSet
     *
     * @param userId
     * @param userNoteLikeZSetKey
     */
    private void asynInitUserNoteLikesZSet(Long userId, String userNoteLikeZSetKey) {
        threadPoolTaskExecutor.execute(() -> {
            // 判断用户笔记点赞 ZSET 是否存在
            Boolean hasKey = redisTemplate.hasKey(userNoteLikeZSetKey);
            // 不存在，则重新初始化
            if (!hasKey) {
                // 查询当前用户最新点赞的 100 篇笔记
                List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectLikedByUserIdAndLimit(userId, 100);
                if (CollUtil.isNotEmpty(noteLikeDOS)) {
                    // 保底1天+随机秒数
                    long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
                    // 构建 Lua 参数
                    Object[] luaArgs = buildNoteLikeZSetLuaArgs(noteLikeDOS, expireSeconds);
                    DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
                    // Lua 脚本路径batch_add_note_like_zset_and_expire.lua 批量添加点赞的笔记到 ZSet 中
                    script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/batch_add_note_like_zset_and_expire.lua")));
                    // 返回值类型
                    script2.setResultType(Long.class);
                    redisTemplate.execute(script2, Collections.singletonList(userNoteLikeZSetKey), luaArgs);
                }

            }
        });
    }
}
