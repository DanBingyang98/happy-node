package com.danby.happynode.count.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.danby.happynode.count.biz.constant.RedisKeyConstants;
import com.danby.happynode.count.biz.domain.dataobject.NoteCountDO;
import com.danby.happynode.count.biz.domain.mapper.NoteCountDOMapper;
import com.danby.happynode.count.biz.service.NoteCountService;
import com.danby.happynode.count.dto.FindNoteCountsByIdRespDTO;
import com.danby.happynode.count.dto.FindNoteCountsByIdsReqDTO;
import com.danby.happynode.framework.common.response.Response;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NoteCountServiceImpl implements NoteCountService {
    @Autowired
    private NoteCountDOMapper noteCountDOMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Response<List<FindNoteCountsByIdRespDTO>> findNoteCountsByIds(FindNoteCountsByIdsReqDTO findNoteCountsByIdsReqDTO) {
        List<Long> noteIds = findNoteCountsByIdsReqDTO.getNoteIds();
        // 1. 先查询 Redis 缓存
        // 构建redisKey
        List<String> countNoteHashKeys = noteIds.stream().map(RedisKeyConstants::buildCountNoteKey).toList();
        // 使用 Pipeline 通道，从 Redis 中批量查询笔记 Hash 计数
        List<Object> countHashes = getCountHashesByRedisPipeline(countNoteHashKeys);
        // 返回参数
        List<FindNoteCountsByIdRespDTO> findNoteCountsByIdRespDTOS = Lists.newArrayList();
        List<Long> needQueryFromDBNoteIds = Lists.newArrayList();
        // 循环入参中需要查询的笔记 ID 集合，构建对应 DTO, 并设置缓存中已存在的计数，以及过滤出需要查数据库的笔记 ID
        for (int i = 0; i < noteIds.size(); i++) {
            Long currNoteId = noteIds.get(i);
            List<Integer> currCountHash = (List<Integer>) countHashes.get(i);
            // 点赞数、收藏数、评论数
            Integer collectTotal = currCountHash.get(0);
            Integer likeTotal = currCountHash.get(1);
            Integer commentTotal = currCountHash.get(2);
            // Hash中有一个计数为null 这条note就需要从数据库查计数
            if (Objects.isNull(collectTotal) || Objects.isNull(likeTotal) || Objects.isNull(commentTotal)) {
                needQueryFromDBNoteIds.add(currNoteId);
            }
            // 构建反参 放入返回参数队列
            FindNoteCountsByIdRespDTO findNoteCountsByIdRespDTO = FindNoteCountsByIdRespDTO.builder()
                    .noteId(currNoteId)
                    .collectTotal(Objects.isNull(collectTotal) ? null : Long.valueOf(collectTotal))
                    .likeTotal(Objects.isNull(likeTotal) ? null : Long.valueOf(likeTotal))
                    .commentTotal(Objects.isNull(commentTotal) ? null : Long.valueOf(commentTotal))
                    .build();
            findNoteCountsByIdRespDTOS.add(findNoteCountsByIdRespDTO);
        }
        // 如果没有需要查数据库计数，表示都在redis缓存中 直接返回计数
        if (CollUtil.isEmpty(needQueryFromDBNoteIds)) {
            return Response.success(findNoteCountsByIdRespDTOS);
        }
        // 2. 若缓存中无，则查询数据库
        List<NoteCountDO> noteCountDOS = noteCountDOMapper.selectByNoteIds(needQueryFromDBNoteIds);
        if (CollUtil.isNotEmpty(noteCountDOS)) {
            // 将数据转换成map noteId -> NoteCountDO
            Map<Long, NoteCountDO> noteCountDOMap = noteCountDOS.stream().collect(Collectors.toMap(NoteCountDO::getNoteId, Function.identity()));
            // 同步到redis缓存
            syncNoteHash2Redis(findNoteCountsByIdRespDTOS, noteCountDOMap);
            // 遍历返回参数，填充计数为null的部分
            for (FindNoteCountsByIdRespDTO findNoteCountsByIdRespDTO : findNoteCountsByIdRespDTOS) {
                Long noteId = findNoteCountsByIdRespDTO.getNoteId();
                Long collectTotal = findNoteCountsByIdRespDTO.getCollectTotal();
                Long likeTotal = findNoteCountsByIdRespDTO.getLikeTotal();
                Long commentTotal = findNoteCountsByIdRespDTO.getCommentTotal();
                NoteCountDO noteCountDO = noteCountDOMap.get(noteId);
                if (Objects.isNull(collectTotal)) {
                    findNoteCountsByIdRespDTO.setCollectTotal(Objects.nonNull(noteCountDO) ? noteCountDO.getCollectTotal() : 0);
                }
                if (Objects.isNull(likeTotal)) {
                    findNoteCountsByIdRespDTO.setLikeTotal(Objects.nonNull(noteCountDO) ? noteCountDO.getLikeTotal() : 0);
                }
                if (Objects.isNull(commentTotal)) {
                    findNoteCountsByIdRespDTO.setCommentTotal(Objects.nonNull(noteCountDO) ? noteCountDO.getCommentTotal() : 0);
                }
            }
        }

        return Response.success(findNoteCountsByIdRespDTOS);
    }

    /***
     * 用户笔记计数同步到redis
     * @param findNoteCountsByIdRespDTOS 从redis获取后的计数 redis中没有的计数内容为null
     * @param noteCountDOMap 从数据库查询得到的计数，转换成 noteId -> noteCountDO
     */
    private void syncNoteHash2Redis(List<FindNoteCountsByIdRespDTO> findNoteCountsByIdRespDTOS, Map<Long, NoteCountDO> noteCountDOMap) {
        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations redisOperations) throws DataAccessException {
                for (FindNoteCountsByIdRespDTO findNoteCountsByIdRespDTO : findNoteCountsByIdRespDTOS) {
                    Long likeTotal = findNoteCountsByIdRespDTO.getLikeTotal();
                    Long collectTotal = findNoteCountsByIdRespDTO.getCollectTotal();
                    Long commentTotal = findNoteCountsByIdRespDTO.getCommentTotal();
                    // 三个都不是null 表示redis中存在计数信息 continue到吓一条
                    if (Objects.nonNull(likeTotal) && Objects.nonNull(collectTotal) && Objects.nonNull(commentTotal)) {
                        continue;
                    }
                    // 否则表示某个计数信息需要从数据同步
                    Long noteId = findNoteCountsByIdRespDTO.getNoteId();
                    String countNoteKey = RedisKeyConstants.buildCountNoteKey(noteId);
                    // 获得noteId对应的数据库DO
                    NoteCountDO noteCountDO = noteCountDOMap.get(noteId);
                    // 定义进行redis操作的countMap
                    HashMap<String, Long> countMap = Maps.newHashMap();
                    // 针对计数为null的部分进行同步
                    if (Objects.isNull(likeTotal)) {
                        countMap.put(RedisKeyConstants.FIELD_LIKE_TOTAL,
                                Objects.nonNull(noteCountDO) ? noteCountDO.getLikeTotal() : 0L);
                    }
                    if (Objects.isNull(collectTotal)) {
                        countMap.put(RedisKeyConstants.FIELD_COLLECT_TOTAL,
                                Objects.nonNull(noteCountDO) ? noteCountDO.getCollectTotal() : 0L);
                    }
                    if (Objects.isNull(commentTotal)) {
                        countMap.put(RedisKeyConstants.FIELD_COMMENT_TOTAL,
                                Objects.nonNull(noteCountDO) ? noteCountDO.getCommentTotal() : 0L);
                    }
                    // 批量添加 Hash 的计数 Field
                    redisOperations.opsForHash().putAll(countNoteKey, countMap);
                    // 设置过期时间
                    long expireTime = 60 * 30 + RandomUtil.randomInt(60 * 30);
                    redisOperations.expire(countNoteKey, expireTime, TimeUnit.SECONDS);
                }
                return null;
            }
        });
    }

    /**
     * 从 Redis 中批量查询笔记 Hash 计数
     *
     * @param countNoteHashKeys
     * @return
     */
    private List<Object> getCountHashesByRedisPipeline(List<String> countNoteHashKeys) {
        return redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                for (String countNoteHashKey : countNoteHashKeys) {
                    operations.opsForHash().multiGet(countNoteHashKey, List.of(
                            RedisKeyConstants.FIELD_COLLECT_TOTAL,
                            RedisKeyConstants.FIELD_LIKE_TOTAL,
                            RedisKeyConstants.FIELD_COMMENT_TOTAL
                    ));
                }
                return null;
            }
        });
    }
}
