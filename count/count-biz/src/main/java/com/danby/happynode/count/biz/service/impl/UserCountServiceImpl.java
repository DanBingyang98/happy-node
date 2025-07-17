package com.danby.happynode.count.biz.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.danby.happynode.count.biz.constant.RedisKeyConstants;
import com.danby.happynode.count.biz.domain.dataobject.UserCountDO;
import com.danby.happynode.count.biz.domain.mapper.UserCountDOMapper;
import com.danby.happynode.count.biz.service.UserCountService;
import com.danby.happynode.count.dto.FindUserCountsByIdReqDTO;
import com.danby.happynode.count.dto.FindUserCountsByIdRespDTO;
import com.danby.happynode.framework.common.response.Response;
import com.google.common.collect.Maps;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class UserCountServiceImpl implements UserCountService {

    @Autowired
    private UserCountDOMapper userCountDOMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;


    @Override
    public Response<FindUserCountsByIdRespDTO> findUserCountData(FindUserCountsByIdReqDTO findUserCountsByIdReqDTO) {
        Long userId = findUserCountsByIdReqDTO.getUserId();
        FindUserCountsByIdRespDTO findUserCountsByIdRespDTO = FindUserCountsByIdRespDTO.builder()
                .userId(userId)
                .build();
        // 先从redis中查
        String countUserKey = RedisKeyConstants.buildCountUserKey(userId);
        List<Object> counts = redisTemplate.opsForHash().multiGet(countUserKey, List.of(
                RedisKeyConstants.FIELD_LIKE_TOTAL,
                RedisKeyConstants.FIELD_COLLECT_TOTAL,
                RedisKeyConstants.FIELD_FANS_TOTAL,
                RedisKeyConstants.FIELD_FOLLOWING_TOTAL,
                RedisKeyConstants.FIELD_NOTE_TOTAL
        ));

        Object likeCount = counts.get(0);
        Object collectCount = counts.get(1);
        Object fansCount = counts.get(2);
        Object followingCount = counts.get(3);
        Object noteCount = counts.get(4);

        findUserCountsByIdRespDTO.setLikeTotal(Objects.isNull(likeCount) ? 0 : Long.parseLong(likeCount.toString()));
        findUserCountsByIdRespDTO.setCollectTotal(Objects.isNull(collectCount) ? 0 : Long.parseLong(collectCount.toString()));
        findUserCountsByIdRespDTO.setFansTotal(Objects.isNull(fansCount) ? 0 : Long.parseLong(fansCount.toString()));
        findUserCountsByIdRespDTO.setFollowingTotal(Objects.isNull(followingCount) ? 0 : Long.parseLong(followingCount.toString()));
        findUserCountsByIdRespDTO.setNoteTotal(Objects.isNull(noteCount) ? 0 : Long.parseLong(noteCount.toString()));

        // 如果redis中计数有null 从数据库查
        boolean hasNull = counts.stream().anyMatch(Objects::isNull);

        if (hasNull) {
            UserCountDO userCountDO = userCountDOMapper.selectByUserId(userId);
            if (Objects.nonNull(userCountDO)) {
                // 判断 Redis 中对应计数，若为空，则使用 DO 中的计数
                if (Objects.isNull(collectCount)) {
                    findUserCountsByIdRespDTO.setCollectTotal(userCountDO.getCollectTotal());
                }
                if (Objects.isNull(fansCount)) {
                    findUserCountsByIdRespDTO.setFansTotal(userCountDO.getFansTotal());
                }
                if (Objects.isNull(noteCount)) {
                    findUserCountsByIdRespDTO.setNoteTotal(userCountDO.getNoteTotal());
                }
                if (Objects.isNull(followingCount)) {
                    findUserCountsByIdRespDTO.setFollowingTotal(userCountDO.getFollowingTotal());
                }
                if (Objects.isNull(likeCount)) {
                    findUserCountsByIdRespDTO.setLikeTotal(userCountDO.getLikeTotal());
                }
                syncHashCount2Redis(countUserKey, userCountDO, collectCount, likeCount, fansCount, followingCount, noteCount);
            }
        }

        return Response.success(findUserCountsByIdRespDTO);
    }

    private void syncHashCount2Redis(String countUserKey, UserCountDO userCountDO, Object collectCount, Object likeCount, Object fansCount, Object followingCount, Object noteCount) {
        if (Objects.nonNull(userCountDO)) {
            threadPoolTaskExecutor.execute(() -> {
                HashMap<String, Long> userCountMap = Maps.newHashMap();
                if (Objects.isNull(collectCount)) {
                    userCountMap.put(RedisKeyConstants.FIELD_COLLECT_TOTAL, Objects.isNull(userCountDO.getCollectTotal()) ? 0 : userCountDO.getCollectTotal());
                }
                if (Objects.isNull(likeCount)) {
                    userCountMap.put(RedisKeyConstants.FIELD_LIKE_TOTAL, Objects.isNull(userCountDO.getLikeTotal()) ? 0 : userCountDO.getLikeTotal());
                }
                if (Objects.isNull(fansCount)) {
                    userCountMap.put(RedisKeyConstants.FIELD_FANS_TOTAL, Objects.isNull(userCountDO.getFansTotal()) ? 0 : userCountDO.getFansTotal());
                }
                if (Objects.isNull(followingCount)) {
                    userCountMap.put(RedisKeyConstants.FIELD_FOLLOWING_TOTAL, Objects.isNull(userCountDO.getFollowingTotal()) ? 0 : userCountDO.getFollowingTotal());
                }
                if (Objects.isNull(noteCount)) {
                    userCountMap.put(RedisKeyConstants.FIELD_NOTE_TOTAL, Objects.isNull(userCountDO.getNoteTotal()) ? 0 : userCountDO.getNoteTotal());
                }
                redisTemplate.executePipelined(new SessionCallback<>() {
                    @Override
                    public Object execute(RedisOperations operations) throws DataAccessException {
                        operations.opsForHash().putAll(countUserKey, userCountMap);
                        // 设置随机过期时间 (2小时以内)
                        long expireTime = 60 * 60 + RandomUtil.randomInt(60 * 60);
                        operations.expire(countUserKey, expireTime, TimeUnit.SECONDS);
                        return null;
                    }
                });
            });
        }
    }
}
