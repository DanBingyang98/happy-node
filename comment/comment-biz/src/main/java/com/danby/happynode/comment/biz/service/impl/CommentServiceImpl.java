package com.danby.happynode.comment.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.danby.framework.context.holder.LoginUserContextHolder;
import com.danby.happynode.comment.biz.constant.MQConstants;
import com.danby.happynode.comment.biz.constant.RedisKeyConstants;
import com.danby.happynode.comment.biz.domain.dataobject.CommentDO;
import com.danby.happynode.comment.biz.domain.mapper.CommentDOMapper;
import com.danby.happynode.comment.biz.domain.mapper.NoteCountDOMapper;
import com.danby.happynode.comment.biz.enums.CommentLevelEnum;
import com.danby.happynode.comment.biz.enums.ResponseCodeEnum;
import com.danby.happynode.comment.biz.model.dto.PublishCommentMqDTO;
import com.danby.happynode.comment.biz.model.vo.*;
import com.danby.happynode.comment.biz.retry.SendMQRetryHelper;
import com.danby.happynode.comment.biz.rpc.DistributedIdGeneratorRpcService;
import com.danby.happynode.comment.biz.rpc.KeyValueRpcService;
import com.danby.happynode.comment.biz.rpc.UserRpcService;
import com.danby.happynode.comment.biz.service.CommentService;
import com.danby.happynode.framework.common.constant.DateConstants;
import com.danby.happynode.framework.common.exception.BusinessException;
import com.danby.happynode.framework.common.response.PageResponse;
import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.framework.common.util.DateUtils;
import com.danby.happynode.framework.common.util.JsonUtils;
import com.danby.happynode.kv.dto.req.FindCommentContentReqDTO;
import com.danby.happynode.kv.dto.resp.FindCommentContentRespDTO;
import com.danby.happynode.user.dto.resp.FindUserByIdRespDTO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CommentServiceImpl implements CommentService {

    @Autowired
    private SendMQRetryHelper sendMQRetryHelper;
    @Autowired
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Autowired
    private NoteCountDOMapper noteCountDOMapper;
    @Autowired
    private CommentDOMapper commentDOMapper;
    @Autowired
    private KeyValueRpcService keyValueRpcService;
    @Autowired
    private UserRpcService userRpcService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    /**
     * 评论详情本地缓存
     */
    private static final Cache<Long, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(10000) // 设置初始容量为 10000 个条目
            .maximumSize(10000) // 设置缓存的最大容量为 10000 个条目
            .expireAfterWrite(1, TimeUnit.HOURS) // 设置缓存条目在写入后 1 小时过期
            .build();


    @Override
    public Response<?> publishComment(PublishCommentReqVO publishCommentReqVO) {
        // 评论正文
        String content = publishCommentReqVO.getContent();
        // 附近图片
        String imageUrl = publishCommentReqVO.getImageUrl();
        // 调用远程rpc服务，生成分布式commentId
        String commentId = distributedIdGeneratorRpcService.getGeneratedCommentId();

        // 评论内容和图片不能同时为空
        Preconditions.checkArgument(StringUtils.isNotBlank(content) || StringUtils.isNotBlank(imageUrl),
                "评论正文和图片不能同时为空");
        Long commentCreatorId = LoginUserContextHolder.getUserId();
        // 发送MQ消息

        // 1. 构建消息体 DTO
        PublishCommentMqDTO publishCommentMqDTO = PublishCommentMqDTO.builder()
                .replyCommentId(publishCommentReqVO.getReplyCommentId())  // 回复的哪个评论（评论 ID）
                .noteId(publishCommentReqVO.getNoteId()) // 所评论的笔记 ID
                .content(content) // 评论的文本内容
                .imageUrl(imageUrl) // 评论的图片url
                .createTime(LocalDateTime.now()) // 创建时间
                .creatorId(commentCreatorId) // 发布评论者的id
                .commentId(Long.valueOf(commentId)) // 评论id
                .build();
        // 2. 通过SendMQRetryHelper发送消息
//        sendMQRetryHelper.send(MQConstants.TOPIC_PUBLISH_COMMENT, publishCommentMqDTO);

        sendMQRetryHelper.asyncSend(MQConstants.TOPIC_PUBLISH_COMMENT, publishCommentMqDTO);

        return Response.success();
    }

    /**
     * 评论列表分页查询
     *
     * @param findCommentPageListReqVO
     * @return
     */
    @Override
    public PageResponse<FindCommentItemRespVO> findCommentPageList(FindCommentPageListReqVO findCommentPageListReqVO) {
        // 笔记 ID
        Long noteId = findCommentPageListReqVO.getNoteId();
        // 当前页码
        Integer pageNo = findCommentPageListReqVO.getPageNo();
        // 每页展示一级评论数
        long pageSize = 10;

        // 先从缓存中查
        String countCommentTotalKey = RedisKeyConstants.buildCountCommentTotalKey(noteId);
        Number commentTotal = (Number) redisTemplate.opsForHash().get(countCommentTotalKey, RedisKeyConstants.FIELD_COMMENT_TOTAL);
        long count = Objects.isNull(commentTotal) ? 0L : commentTotal.longValue();
        // 如果缓存不存在，则从数据库查
        if (Objects.isNull(commentTotal)) {
            Long dbCount = noteCountDOMapper.selectCommentTotalByNoteId(noteId);
            if (Objects.isNull(dbCount)) {
                throw new BusinessException(ResponseCodeEnum.COMMENT_NOT_FOUND);
            }
            count = dbCount;
            threadPoolTaskExecutor.execute(() ->
                    syncCommentTotalToRedis(countCommentTotalKey, dbCount));
        }
        // 如果评论总数为0 直接响应
        if (count == 0L) {
            return PageResponse.success(null, pageNo, pageSize);
        }
        // 分页返参
        List<FindCommentItemRespVO> commentRespVOS = null;
        if (count > 0) {
            commentRespVOS = Lists.newArrayList();
            long offset = PageResponse.getOffset(pageNo, pageSize);
            // 从redis缓存中查
            String commentListKey = RedisKeyConstants.buildCommentListKey(noteId);
            Boolean hasKey = redisTemplate.hasKey(commentListKey);
            // 若 ZSET 不存在 异步将热点评论同步到 redis 中（最多同步 500 条）
            if (!hasKey) {
                threadPoolTaskExecutor.execute(() ->
                        syncHeatComments2Redis(commentListKey, noteId));
            }

            // 若 ZSET 缓存存在, 并且查询的是前 500 页的评论
            if (hasKey && offset < 500) {
                // 使用 ZRevRange 获取某篇笔记下，按热度降序排序的一级评论 ID
                Set<Object> commentIds = redisTemplate.opsForZSet()
                        .reverseRangeByScore(commentListKey, -Double.MAX_VALUE, Double.MAX_VALUE, offset, pageSize);
                if (CollUtil.isNotEmpty(commentIds)) {
                    // Set 转 List
                    ArrayList<Object> commentIdList = Lists.newArrayList(commentIds);
                    // 先查询本地缓存
                    // 新建一个集合 保存本地缓存中没有的commentId
                    List<Long> localCacheExpiredCommentIds = Lists.newArrayList();
                    // 构建查询本地缓存的参数
                    List<Long> localCacheKeys = commentIdList.stream().map(commentId -> Long.valueOf(commentId.toString())).toList();
                    // 批量查询本地缓存
                    Map<Long, String> commentIdAndDetailJsonMap = LOCAL_CACHE.getAll(localCacheKeys, missingKeys -> {
                        // 对于本地缓存确实的key 返回空字符串
                        Map<Long, String> missingData = Maps.newHashMap();
                        for (Long missingKey : missingKeys) {
                            // 记录缓存中不存在的评论 ID
                            localCacheExpiredCommentIds.add(missingKey);
                            // 不存在的评论详情, 对其 Value 值设置为空字符串
                            missingData.put(missingKey, Strings.EMPTY);
                        }
                        return missingData;
                    });
                    // 若 localCacheExpiredCommentIds 的大小不等于 commentIdList 的大小，说明本地缓存中有数据
                    if (CollUtil.size(localCacheExpiredCommentIds) != commentIdList.size()) {
                        // 将本地缓存中的评论详情 Json, 转换为实体类，添加到 VO 返参集合中
                        for (String jsonStr : commentIdAndDetailJsonMap.values()) {
                            if (StringUtils.isBlank(jsonStr)) continue;
                            FindCommentItemRespVO findCommentItemRespVO = JsonUtils.parseObject(jsonStr, FindCommentItemRespVO.class);
                            commentRespVOS.add(findCommentItemRespVO);
                        }
                    }
                    // 如果localCacheExpiredCommentIds 大小等于0 说明数据都在本地缓存中，则直接返回响应
                    if (CollUtil.isEmpty(localCacheExpiredCommentIds)) {
                        // 计数信息需要从redis中查找
                        if (CollUtil.isNotEmpty(commentRespVOS)) {
                            setCommentCountData(commentRespVOS, localCacheExpiredCommentIds);
                        }
                        return PageResponse.success(commentRespVOS, pageNo, count, pageSize);
                    }
                    // 构建 MGET 批量查询评论详情的 Key 集合 从localCacheExpireCommentIds 中拿到
                    // 只需要查询本地换从中没有的数据即可 即从localCacheExpireCommentIds
                    List<String> commentIdKeys = localCacheExpiredCommentIds.stream().map(RedisKeyConstants::buildCommentDetailKey).toList();
                    // MGET 批量获取评论数据
                    List<Object> commentsJsonList = redisTemplate.opsForValue().multiGet(commentIdKeys);
                    // 可能存在部分评论不在缓存中，已经过期被删除，这些评论 ID 需要提取出来，等会查数据库
                    List<Long> expiredCommentIds = new ArrayList<>();
                    for (int i = 0; i < commentsJsonList.size(); i++) {
                        String commentJson = (String) commentsJsonList.get(i);
                        if (Objects.nonNull(commentJson)) {
                            // 缓存中存在的评论 Json，直接转换为 VO 添加到返参集合中
                            FindCommentItemRespVO findCommentItemRespVO = JsonUtils.parseObject(commentJson, FindCommentItemRespVO.class);
                            commentRespVOS.add(findCommentItemRespVO);
                        } else {
                            // 评论失效，添加到失效评论列表
                            expiredCommentIds.add(Long.valueOf(commentIdList.get(i).toString()));
                        }
                    }
                    //在redis中存在的一级评论详情 需要再次从redis查询其计数信息
                    if (CollUtil.isNotEmpty(commentRespVOS)) {
                        setCommentCountData(commentRespVOS, expiredCommentIds);
                    }

                    // 对于换从中不存在的一级评论 需要批量从数据库中查 并添加到commentRespVOS中
                    if (CollUtil.isNotEmpty(expiredCommentIds)) {
                        List<CommentDO> commentDOS = commentDOMapper.selectByCommentIds(expiredCommentIds);
                        getCommentDataAndSync2Redis(commentDOS, noteId, commentRespVOS);
                    }
                }
                // 按热度值进行降序排列
                commentRespVOS = commentRespVOS.stream()
                        .sorted(Comparator.comparing(FindCommentItemRespVO::getHeat).reversed())
                        .collect(Collectors.toList());
                // 异步将评论详情缓存到本地
                syncCommentDetail2LocalCache(commentRespVOS);
                return PageResponse.success(commentRespVOS, pageNo, count, pageSize);
            }
            // 缓存中没有，则查询数据库
            // 查询一级评论
            List<CommentDO> levelOneCommentDOS = commentDOMapper.selectPageList(noteId, offset, pageSize);
            getCommentDataAndSync2Redis(levelOneCommentDOS, noteId, commentRespVOS);

        }
        // 异步将评论详情缓存到本地
        syncCommentDetail2LocalCache(commentRespVOS);
        return PageResponse.success(commentRespVOS, pageNo, count, pageSize);
    }

    /**
     * 二级评论分页查询
     *
     * @param findChildCommentPageListReqVO
     * @return
     */
    @Override
    public PageResponse<FindChildCommentItemRespVO> findChildCommentPageList(FindChildCommentPageListReqVO findChildCommentPageListReqVO) {
        // 一级评论id
        Long parentCommentId = findChildCommentPageListReqVO.getParentCommentId();
        // 页码
        Integer pageNo = findChildCommentPageListReqVO.getPageNo();
        // 每页展示 10 条数据
        int pageSize = 6;
        // 获取偏移量  需要 +1，因为最早回复的二级评论已经被展示了
        long offset = PageResponse.getOffset(pageNo, pageSize) + 1;
        // 分页返参 VO
        List<FindChildCommentItemRespVO> childCommentRespVOS = Lists.newArrayList();
        // TODO 从缓存中查

        // 查询一级评论下子评论的总数 (直接查询 t_comment 表的 child_comment_total 字段，提升查询性能, 避免 count(*))
        Long childCommentTotal = commentDOMapper.selectChildCommentTotalById(parentCommentId);
        if (Objects.isNull(childCommentTotal) || childCommentTotal == 0)
            return PageResponse.success(null, pageNo, 0);

        // 分页返参 VO
        List<FindChildCommentItemRespVO> childCommentItemRespVOS = new ArrayList<>();
        // 分页查询子评论
        List<CommentDO> childCommentDOS = commentDOMapper.selectChildCommentPageList(parentCommentId, offset, pageSize);
        // 调用KV服务获取评论内容 调用用户服务获取评论者信息
        // 调用KV服务的入参
        List<FindCommentContentReqDTO> findCommentContentReqDTOS = Lists.newArrayList();
        // 调用用户服务的入参
        Set<Long> userIds = Sets.newHashSet();
        Long noteId = childCommentDOS.getFirst().getNoteId();
        childCommentDOS.forEach(childCommentDO -> {
            // 构建调用 KV 服务批量查询评论内容的入参
            if (!childCommentDO.getIsContentEmpty()) {
                FindCommentContentReqDTO findCommentContentReqDTO = FindCommentContentReqDTO.builder()
                        .yearMonth(DateConstants.DATE_FORMAT_Y_M.format(childCommentDO.getCreateTime()))
                        .contentId(childCommentDO.getContentUuid())
                        .build();
                findCommentContentReqDTOS.add(findCommentContentReqDTO);
            }
            // 构建调用用户服务批量查询用户信息的入参 (包含评论发布者、回复的目标用户)
            userIds.add(childCommentDO.getUserId());
            Long parentId = childCommentDO.getParentId();
            Long replyCommentId = childCommentDO.getReplyCommentId();
            // 若当前评论的 replyCommentId 不等于 parentId，则前端需要展示回复的哪个用户，如  “回复 犬小哈：”
            if (!Objects.equals(parentId, replyCommentId)) {
                userIds.add(childCommentDO.getReplyUserId());
            }
        });
        // RPC调用KV服务获取评论内容
        List<FindCommentContentRespDTO> findCommentContentRespDTOS =
                keyValueRpcService.batchFindCommentContent(noteId, findCommentContentReqDTOS);
        // 将查询结果转换成map 方便后续拼装响应数据
        Map<String, String> commentUuidAndContentMap = null;
        if (CollUtil.isNotEmpty(findCommentContentRespDTOS))
            commentUuidAndContentMap = findCommentContentRespDTOS.stream()
                    .collect(Collectors.toMap(FindCommentContentRespDTO::getContentId, FindCommentContentRespDTO::getContent));
        // RPC 调用用户服务获取用户信息
        List<FindUserByIdRespDTO> findUserByIdRespDTOS = userRpcService.findUserByIds(userIds.stream().toList());
        // 将查询结果转换成map 方便后续拼装响应数据
        Map<Long, FindUserByIdRespDTO> userIdAndUserInfoMap = null;
        if (CollUtil.isNotEmpty(findUserByIdRespDTOS))
            userIdAndUserInfoMap = findUserByIdRespDTOS.stream()
                    .collect(Collectors.toMap(FindUserByIdRespDTO::getId, dto -> dto));
        // TODO 拼装响应VO
        for (CommentDO childCommentDO : childCommentDOS) {
            // 构建 VO 实体类
            Long userId = childCommentDO.getUserId();
            FindChildCommentItemRespVO findChildCommentItemRespVO = FindChildCommentItemRespVO.builder()
                    .userId(userId)
                    .commentId(childCommentDO.getId())
                    .imageUrl(childCommentDO.getImageUrl())
                    .createTime(DateUtils.formatRelativeTime(childCommentDO.getCreateTime()))
                    .likeTotal(childCommentDO.getLikeTotal())
                    .build();
            // 填充用户信息(包括评论发布者、回复的用户)
            if (CollUtil.isNotEmpty(userIdAndUserInfoMap)) {
                FindUserByIdRespDTO findUserByIdRespDTO = userIdAndUserInfoMap.get(userId);
                // 评论发布者用户信息(头像、昵称)
                if (Objects.nonNull(findUserByIdRespDTO)) {
                    findChildCommentItemRespVO.setAvatar(findUserByIdRespDTO.getAvatar());
                    findChildCommentItemRespVO.setNickName(findUserByIdRespDTO.getNickName());
                }
                // 评论回复的哪个
                Long parentId = childCommentDO.getParentId();
                Long replyCommentId = childCommentDO.getReplyCommentId();
                if (Objects.nonNull(replyCommentId) && !Objects.equals(parentId, replyCommentId)) {
                    Long replyUserId = childCommentDO.getReplyUserId();
                    FindUserByIdRespDTO replyUserDO = userIdAndUserInfoMap.get(replyUserId);
                    findChildCommentItemRespVO.setReplyUserId(replyUserId);
                    findChildCommentItemRespVO.setReplyUserName(replyUserDO.getNickName());
                }
            }
            // 评论内容
            if (CollUtil.isNotEmpty(commentUuidAndContentMap)) {
                String contentUuid = childCommentDO.getContentUuid();
                if (StringUtils.isNoneBlank(contentUuid)) {
                    String content = commentUuidAndContentMap.get(contentUuid);
                    findChildCommentItemRespVO.setContent(content);
                }
            }
            childCommentRespVOS.add(findChildCommentItemRespVO);
        }
        return PageResponse.success(childCommentRespVOS, pageNo, childCommentTotal, pageSize);
    }

    /**
     * 获取全部评论数据，并将评论详情同步到 Redis 中
     *
     * @param levelOneCommentDOS
     * @param noteId
     * @param commentRespVOS
     */
    private void getCommentDataAndSync2Redis(List<CommentDO> levelOneCommentDOS, Long noteId, List<FindCommentItemRespVO> commentRespVOS) {
        // 过滤出所有最早回复的二级评论 ID
        List<Long> levelTwoCommentIds = levelOneCommentDOS.stream()
                .map(CommentDO::getFirstReplyCommentId)
                .filter(firstReplyCommentId -> firstReplyCommentId != 0)
                .toList();
        // 查询二级评论
        Map<Long, CommentDO> commentIdAndDOMap = null;
        List<CommentDO> levelTwoCommentDOS = null;
        if (!levelTwoCommentIds.isEmpty()) {
            levelTwoCommentDOS = commentDOMapper.selectByCommentIds(levelTwoCommentIds);
            // 转 Map 集合，方便后续拼装数据
            commentIdAndDOMap = levelTwoCommentDOS.stream()
                    .collect(Collectors.toMap(CommentDO::getId, commentDO -> commentDO));
        }
        // 调用 KV 服务需要的入参
        List<FindCommentContentReqDTO> findCommentContentReqDTOs = new ArrayList<>();
        // 调用用户服务的入参
        List<Long> userIds = new ArrayList<>();
        // 将一级评论和二级评论合并到一起
        List<CommentDO> allCommentDOS = new ArrayList<>();
        allCommentDOS.addAll(levelOneCommentDOS);
        allCommentDOS.addAll(levelTwoCommentDOS);
        // 循环提取 RPC 调用需要的入参数据
        allCommentDOS.forEach(commentDO -> {
            // 构建调用 KV 服务批量查询评论内容的入参
            Boolean isContentEmpty = commentDO.getIsContentEmpty();
            if (!isContentEmpty) {
                FindCommentContentReqDTO findCommentContentReqDTO = FindCommentContentReqDTO.builder()
                        .contentId(commentDO.getContentUuid())
                        .yearMonth(DateConstants.DATE_FORMAT_Y_M.format(commentDO.getCreateTime()))
                        .build();
                findCommentContentReqDTOs.add(findCommentContentReqDTO);
            }
            // 构建调用用户服务批量查询用户信息的入参
            userIds.add(commentDO.getUserId());
        });
        // RPC: 调用 KV 服务，批量获取评论内容
        List<FindCommentContentRespDTO> findCommentContentRespDTOS = keyValueRpcService.batchFindCommentContent(noteId, findCommentContentReqDTOs);
        // DTO 集合转 Map, 方便后续拼装数据
        Map<String, String> commentUuidAndContentMap = null;
        if (CollUtil.isNotEmpty(findCommentContentRespDTOS)) {
            commentUuidAndContentMap = findCommentContentRespDTOS.stream().collect(Collectors.toMap(FindCommentContentRespDTO::getContentId, FindCommentContentRespDTO::getContent));
        }
        // RPC: 调用用户服务，批量获取用户信息
        List<FindUserByIdRespDTO> findUserByIdRespDTOS = userRpcService.findUserByIds(userIds);
        // DTO 集合转 Map, 方便后续拼装数据
        Map<Long, FindUserByIdRespDTO> userIdAndDTOMap = null;
        if (CollUtil.isNotEmpty(findUserByIdRespDTOS)) {
            userIdAndDTOMap = findUserByIdRespDTOS.stream().collect(Collectors.toMap(FindUserByIdRespDTO::getId, dto -> dto));
        }
        // DO 转 VO 组装数据
        for (CommentDO levelOneCommentDO : levelOneCommentDOS) {
            // 一级评论
            Long userId = levelOneCommentDO.getUserId();
            FindCommentItemRespVO oneLevelCommentRspVO = FindCommentItemRespVO.builder()
                    .userId(userId)
                    .commentId(levelOneCommentDO.getId())
                    .imageUrl(levelOneCommentDO.getImageUrl())
                    .createTime(DateUtils.formatRelativeTime(levelOneCommentDO.getCreateTime()))
                    .likeTotal(levelOneCommentDO.getLikeTotal())
                    .childCommentTotal(levelOneCommentDO.getChildCommentTotal())
                    .heat(levelOneCommentDO.getHeat())
                    .build();
            // 用户信息
            setUserInfo(commentIdAndDOMap, userIdAndDTOMap, userId, oneLevelCommentRspVO);
            // 笔记内容
            setCommentContent(commentUuidAndContentMap, levelOneCommentDO, oneLevelCommentRspVO);

            // 二级评论
            Long firstReplyCommentId = levelOneCommentDO.getFirstReplyCommentId();
            if (CollUtil.isNotEmpty(commentIdAndDOMap)) {
                CommentDO firstReplyCommentDO = commentIdAndDOMap.get(firstReplyCommentId);
                if (Objects.nonNull(firstReplyCommentDO)) {
                    Long firstReplyCommentUserId = firstReplyCommentDO.getUserId();
                    FindCommentItemRespVO firstReplyCommentRespVO = FindCommentItemRespVO.builder()
                            .userId(firstReplyCommentUserId)
                            .commentId(firstReplyCommentDO.getId())
                            .imageUrl(firstReplyCommentDO.getImageUrl())
                            .createTime(DateUtils.formatRelativeTime(firstReplyCommentDO.getCreateTime()))
                            .likeTotal(firstReplyCommentDO.getLikeTotal())
                            .childCommentTotal(firstReplyCommentDO.getChildCommentTotal())
                            .heat(firstReplyCommentDO.getHeat())
                            .build();
                    // 用户信息
                    setUserInfo(commentIdAndDOMap, userIdAndDTOMap, firstReplyCommentUserId, firstReplyCommentRespVO);
                    // 笔记内容
                    setCommentContent(commentUuidAndContentMap, firstReplyCommentDO, firstReplyCommentRespVO);
                    // 二级评论VO添加到一级评论VO
                    oneLevelCommentRspVO.setFirstReplyComment(firstReplyCommentRespVO);
                }
            }
            commentRespVOS.add(oneLevelCommentRspVO);

            // 异步将笔记详情 存入redis缓存
            threadPoolTaskExecutor.execute(() -> {
                // 准备写入的数据
                Map<String, String> data = Maps.newHashMap();
                commentRespVOS.forEach(commentRespVO -> {
                    Long commentId = commentRespVO.getCommentId();
                    String key = RedisKeyConstants.buildCommentDetailKey(commentId);
                    data.put(key, JsonUtils.toJsonString(commentRespVO));
                });
                // 使用pipeline方式写入redis缓存
                redisTemplate.executePipelined((RedisCallback<Object>) (connection) -> {
                    for (Map.Entry<String, String> entry : data.entrySet()) {
                        // 将 Java 对象序列化为 JSON 字符串
                        String jsonString = JsonUtils.toJsonString(entry.getValue());
                        // 随机生成过期时间 (5小时以内)
                        int randomExpire = RandomUtil.randomInt(5 * 60 * 60);
                        // 批量写入并设置过期时间
                        connection.setEx(
                                redisTemplate.getStringSerializer().serialize(entry.getKey()),
                                randomExpire,
                                redisTemplate.getStringSerializer().serialize(jsonString)
                        );
                    }
                    return null;
                });
            });
        }
    }

    private void syncHeatComments2Redis(String countCommentTotalKey, Long noteId) {
        List<CommentDO> commentDOS = commentDOMapper.selectHeatComments(noteId);
        if (CollUtil.isNotEmpty(commentDOS)) {
            redisTemplate.executePipelined((RedisCallback<?>) connection -> {
                // 先判断 key 是否存在
                Boolean exists = redisTemplate.hasKey(countCommentTotalKey);
                if (exists != null && exists) {
                    // 如果 key 存在，删除它以避免 WRONGTYPE 异常
                    redisTemplate.delete(countCommentTotalKey);
                }

                ZSetOperations<String, Object> zSetOperations = redisTemplate.opsForZSet();
                for (CommentDO commentDO : commentDOS) {
                    Double commentHeat = commentDO.getHeat();
                    Long commentId = commentDO.getId();
                    zSetOperations.add(countCommentTotalKey, commentId, commentHeat);
                }
                // 设置随机过期时间 单位：秒  5小时以内
                int expireSeconds = RandomUtil.randomInt(5 * 60 * 60);
                redisTemplate.expire(countCommentTotalKey, expireSeconds, TimeUnit.SECONDS);
                return null;
            });
        }
    }

    /**
     * 同步笔记评论总数到 Redis 中
     *
     * @param countCommentTotalKey
     * @param dbCount
     */
    private void syncCommentTotalToRedis(String countCommentTotalKey, Long dbCount) {
//        redisTemplate.opsForHash().put(countCommentTotalKey, RedisKeyConstants.FIELD_COMMENT_TOTAL, dbCount);
        redisTemplate.executePipelined(new SessionCallback() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                // 批量设置
                operations.opsForHash()
                        .put(countCommentTotalKey, RedisKeyConstants.FIELD_COMMENT_TOTAL, dbCount);
                // 过期时间（保底1小时 + 随机秒数，将缓存过期时间打散，防止同一时间大量缓存失效，导致数据库压力太大）
                long expireTime = 60 * 60 + RandomUtil.randomInt(4 * 60 * 60);
                operations.expire(countCommentTotalKey, expireTime, TimeUnit.SECONDS);
                return null;
            }
        });
    }

    /**
     * 设置评论内容
     *
     * @param commentUuidAndContentMap
     * @param commentDO
     * @param findCommentItemRespVO
     */
    private void setCommentContent(Map<String, String> commentUuidAndContentMap, CommentDO commentDO, FindCommentItemRespVO findCommentItemRespVO) {
        if (CollUtil.isNotEmpty(commentUuidAndContentMap)) {
            String contentUuid = commentDO.getContentUuid();
            if (StrUtil.isNotBlank(contentUuid)) {
                String content = commentUuidAndContentMap.get(contentUuid);
                if (StrUtil.isNotBlank(content)) {
                    findCommentItemRespVO.setContent(content);
                }
            }
        }
    }

    /**
     * 设置用户信息
     *
     * @param commentIdAndDOMap
     * @param userIdAndDTOMap
     * @param userId
     * @param findCommentItemRespVO
     */
    private void setUserInfo(Map<Long, CommentDO> commentIdAndDOMap, Map<Long, FindUserByIdRespDTO> userIdAndDTOMap, Long userId, FindCommentItemRespVO findCommentItemRespVO) {
        FindUserByIdRespDTO findUserByIdRespDTO = userIdAndDTOMap.get(userId);
        if (Objects.nonNull(findUserByIdRespDTO)) {
            findCommentItemRespVO.setNickname(findUserByIdRespDTO.getNickName());
            findCommentItemRespVO.setAvatar(findUserByIdRespDTO.getAvatar());
        }
    }

    /**
     * 同步评论详情到本地缓存中
     *
     * @param commentRespVOS
     */
    private void syncCommentDetail2LocalCache(List<FindCommentItemRespVO> commentRespVOS) {
        // 开启一个异步线程
        threadPoolTaskExecutor.execute(() -> {
            // 构建缓存所需的键值
            HashMap<Long, String> localCacheData = Maps.newHashMap();
            commentRespVOS.forEach(commentRespVO -> {
                localCacheData.put(commentRespVO.getCommentId(), JSON.toJSONString(commentRespVO));
            });
            // 批量添加缓存数据
            LOCAL_CACHE.putAll(localCacheData);
        });
    }

    /**
     * 设置评论 VO 的计数
     *
     * @param commentVOS        返参 VO 集合
     * @param expiredCommentIds 缓存中已失效的评论 ID 集合
     */
    private void setCommentCountData(List<FindCommentItemRespVO> commentVOS, List<Long> expiredCommentIds) {
        // 准备从评论 Hash 中查询计数 (子评论总数、被点赞数)
        // 缓存中存在的评论 ID
        List<Long> existCommentIds = Lists.newArrayList();
        // 遍历从缓存中解析出的 VO 集合，提取一级、二级评论 ID
        for (FindCommentItemRespVO commentVO : commentVOS) {
            Long commentId = commentVO.getCommentId();
            existCommentIds.add(commentId);
            FindCommentItemRespVO firstReplyCommentVO = commentVO.getFirstReplyComment();
            if (Objects.nonNull(firstReplyCommentVO)) {
                existCommentIds.add(firstReplyCommentVO.getCommentId());
            }
        }
        // 已失效的Hash评论ID
        List<Long> expiredCountCommentIds = Lists.newArrayList();
        // 构建需要查询的HashKey集合
        List<String> countCommentHashKeys = existCommentIds.stream()
                .map(RedisKeyConstants::buildCountCommentKey)
                .toList();
        // 使用redis查询
        List<Object> results = redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                // 遍历需要查询的评论计数的 Hash 键集合
                // 在管道中执行 Redis 的 hash.entries 操作，此操作会获取指定 Hash 键中所有的字段和值
                countCommentHashKeys.forEach(key -> operations.opsForHash().entries(key));
                return null;
            }
        });
        // 评论id - 计数数据字典
        Map<Long, Map<Object, Object>> commentIdAndCountMap = Maps.newHashMap();
        // 遍历未过期的评论 ID 集合
        for (int i = 0; i < existCommentIds.size(); i++) {
            // 当前评论 ID
            Long currentId = Long.valueOf(existCommentIds.get(i).toString());
            // 从缓存查询结果中，获取对应 Hash
            Map<Object, Object> hash = (Map<Object, Object>) results.get(i);
            if (CollUtil.isEmpty(hash)) {
                expiredCountCommentIds.add(currentId);
                continue;
            }
            // 若存在，则将数据添加到 commentIdAndCountMap 中，方便后续读取
            commentIdAndCountMap.put(currentId, hash);
        }

        // 若已过期的计数评论 ID 集合大于 0，说明部分计数数据不在 Redis 缓存中
        // 需要查询数据库，并将这部分的评论计数 Hash 同步到 Redis 中
        if (CollUtil.isNotEmpty(expiredCountCommentIds)) {
            List<CommentDO> commentDOS = commentDOMapper.selectCommentCountByIds(expiredCountCommentIds);
            commentDOS.forEach(commentDO -> {
                Long commentDOId = commentDO.getId();
                HashMap<Object, Object> map = Maps.newHashMap();
                map.put(RedisKeyConstants.FIELD_LIKE_TOTAL, commentDO.getLikeTotal());
                Integer level = commentDO.getLevel();
                // 一级评论 添加评论总数
                if (Objects.equals(level, CommentLevelEnum.ONE.getCode())) {
                    map.put(RedisKeyConstants.FIELD_CHILD_COMMENT_TOTAL, commentDO.getChildCommentTotal());

                }
                commentIdAndCountMap.put(commentDOId, map);
            });
            threadPoolTaskExecutor.execute(() -> {
                redisTemplate.executePipelined(new SessionCallback<>() {
                    @Override
                    public Object execute(RedisOperations operations) throws DataAccessException {
                        commentDOS.forEach(commentDO -> {
                            String key = RedisKeyConstants.buildCountCommentKey(commentDO.getId());
                            Integer level = commentDO.getLevel();
                            // 设置 Field 数据
                            HashMap<String, Long> map = Maps.newHashMap();
                            map.put(RedisKeyConstants.FIELD_LIKE_TOTAL, commentDO.getLikeTotal());
                            // 一级评论 添加评论总数
                            if (Objects.equals(level, CommentLevelEnum.ONE.getCode())) {
                                map.put(RedisKeyConstants.FIELD_CHILD_COMMENT_TOTAL, commentDO.getChildCommentTotal());
                            }
                            operations.opsForHash().putAll(key, map);
                        });
                        return null;
                    }
                });
            });
        }

        // 遍历VO 设置对应的评论的二级评论数喝点赞数
        for (FindCommentItemRespVO commentVO : commentVOS) {
            Long commentId = commentVO.getCommentId();
            // 若当前这条评论是从数据库中查询出来的, 则无需设置二级评论数、点赞数，以数据库查询出来的为主
            if (CollUtil.isEmpty(expiredCommentIds) && expiredCommentIds.contains(commentId))
                continue;
            // 拿到评论的计数map
            Map<Object, Object> countMap = commentIdAndCountMap.get(commentId);
            // 获取并转换点赞数
            Object likeTotalObj = countMap.get(RedisKeyConstants.FIELD_LIKE_TOTAL);
            Long likeTotal = likeTotalObj == null ? 0 : Long.parseLong(likeTotalObj.toString());
            // 获取并转换二级评论数
            Object childCommentTotalObj = countMap.get(RedisKeyConstants.FIELD_CHILD_COMMENT_TOTAL);
            Long childCommentTotal = childCommentTotalObj == null ? 0 : Long.parseLong(childCommentTotalObj.toString());
            // 设置到VO
            commentVO.setLikeTotal(likeTotal);
            commentVO.setChildCommentTotal(childCommentTotal);
            // 拿到评论的第一个回复
            FindCommentItemRespVO firstReplyCommentVO = commentVO.getFirstReplyComment();
            if (Objects.nonNull(firstReplyCommentVO)) {
                // 拿到第一个回复的评论ID
                Long firstReplyCommentId = firstReplyCommentVO.getCommentId();
                // 拿到第一个回复的计数map
                Map<Object, Object> firstReplyCommentCountMap = commentIdAndCountMap.get(firstReplyCommentId);
                if (CollUtil.isNotEmpty(firstReplyCommentCountMap)) {
                    // 获取并转换点赞数
                    Object firstReplyCommentLikeTotalObj = firstReplyCommentCountMap.get(RedisKeyConstants.FIELD_LIKE_TOTAL);
                    Long firstReplyCommentLikeTotal = firstReplyCommentLikeTotalObj == null ? 0 : Long.parseLong(firstReplyCommentLikeTotalObj.toString());
                    // 设置到VO
                    firstReplyCommentVO.setLikeTotal(firstReplyCommentLikeTotal);
                }
            }
        }
    }
}
