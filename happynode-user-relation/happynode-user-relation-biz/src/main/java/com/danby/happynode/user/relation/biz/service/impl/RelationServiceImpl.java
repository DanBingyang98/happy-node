package com.danby.happynode.user.relation.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.danby.framework.context.holder.LoginUserContextHolder;
import com.danby.happynode.framework.common.exception.BusinessException;
import com.danby.happynode.framework.common.response.PageResponse;
import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.framework.common.util.DateUtils;
import com.danby.happynode.framework.common.util.JsonUtils;
import com.danby.happynode.user.dto.resp.FindUserByIdRespDTO;
import com.danby.happynode.user.relation.biz.constant.MQConstant;
import com.danby.happynode.user.relation.biz.constant.RedisKeyConstants;
import com.danby.happynode.user.relation.biz.domain.dataobject.FansDO;
import com.danby.happynode.user.relation.biz.domain.dataobject.FollowingDO;
import com.danby.happynode.user.relation.biz.domain.mapper.FansDOMapper;
import com.danby.happynode.user.relation.biz.domain.mapper.FollowingDOMapper;
import com.danby.happynode.user.relation.biz.enums.LuaResultEnum;
import com.danby.happynode.user.relation.biz.enums.ResponseCodeEnum;
import com.danby.happynode.user.relation.biz.model.dto.FollowUserMqDTO;
import com.danby.happynode.user.relation.biz.model.dto.UnfollowUserMqDTO;
import com.danby.happynode.user.relation.biz.model.vo.*;
import com.danby.happynode.user.relation.biz.rpc.UserRpcService;
import com.danby.happynode.user.relation.biz.service.RelationService;
import lombok.extern.slf4j.Slf4j;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
public class RelationServiceImpl implements RelationService {

    @Autowired
    private UserRpcService userRpcService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private FollowingDOMapper followingDOMapper;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Autowired
    private FansDOMapper fansDOMapper;

    @Override
    public Response<?> follow(FollowUserReqVO followUserReqVO) {
        // 关注的用户 ID
        Long followUserId = followUserReqVO.getFollowUserId();
        // 当前登录的用户 ID
        Long userId = LoginUserContextHolder.getUserId();
        // 校验：无法关注自己
        if (Objects.equals(userId, followUserId)) {
            throw new BusinessException(ResponseCodeEnum.CANT_FOLLOW_YOUR_SELF);
        }
        // 校验关注的用户是否存在
        FindUserByIdRespDTO findUserByIdRespDTO = userRpcService.findById(userId);
        // 关注的用户不存在，抛出业务异常
        if (Objects.isNull(findUserByIdRespDTO)) {
            throw new BusinessException(ResponseCodeEnum.FOLLOW_USER_NOT_EXISTED);
        }
        // 构建当前用户关注列表的 Redis Key
        String userFollowingRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        // 设置 Lua 脚本路径
        redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_check_and_add.lua")));
        // 设置返回值类型
        redisScript.setResultType(Long.class);
        // 当前时间转时间戳timestamp
        LocalDateTime now = LocalDateTime.now();
        long timestamp = DateUtils.localDateTime2Timestamp(now);
        // 执行 Lua 脚本，拿到返回结果
        Long luaResult = redisTemplate.execute(redisScript, Collections.singletonList(userFollowingRedisKey), followUserId, timestamp);
        LuaResultEnum luaResultEnum = LuaResultEnum.valueOf(luaResult);
        if (Objects.isNull(luaResultEnum)) throw new RuntimeException("Lua 返回结果错误");
        // 判断返回结果
        switch (luaResultEnum) {
            // 校验关注数是否已经达到上限
            case FOLLOW_LIMIT -> throw new BusinessException(ResponseCodeEnum.FOLLOWING_COUNT_LIMIT);
            // 已经关注了该用户
            case ALREADY_FOLLOWED -> throw new BusinessException(ResponseCodeEnum.ALREADY_FOLLOWED);
            // ZSet 关注列表不存在
            case ZSET_NOT_EXIST -> {
                // 写入 Redis ZSET 关注列表
                List<FollowingDO> followingDOS = followingDOMapper.selectByUserId(followUserId);
                // 随机过期时间
                // 保底1天+随机秒数
                long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
                // 若记录为空，直接 ZADD 关系数据, 并设置过期时间
                if (CollUtil.isEmpty(followingDOS)) {
                    DefaultRedisScript<Long> redisScript2 = new DefaultRedisScript<>();
                    redisScript2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_add_and_expire.lua")));
                    redisScript2.setResultType(Long.class);
                    // 可以根据用户类型，设置不同的过期时间，若当前用户为大V, 则可以过期时间设置的长些或者不设置过期时间；如不是，则设置的短些
                    // 如何判断呢？可以从计数服务获取用户的粉丝数，目前计数服务还没创建，则暂时采用统一的过期策略
                    redisTemplate.execute(redisScript2, Collections.singletonList(userFollowingRedisKey), followUserId, timestamp, expireSeconds);
                } else { // 若记录不为空，则将关注关系数据全量同步到 Redis 中，并设置过期时间；
                    // 构建 Lua 参数
                    Object[] luaArgs = buildLuaArgs(followingDOS, expireSeconds);

                    // 执行 Lua 脚本，批量同步关注关系数据到 Redis 中
                    DefaultRedisScript<Long> redisScript3 = new DefaultRedisScript<>();
                    redisScript3.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
                    redisScript3.setResultType(Long.class);
                    redisTemplate.execute(redisScript3, Collections.singletonList(userFollowingRedisKey), luaArgs);

                    // 再次调用上面的 Lua 脚本：follow_check_and_add.lua , 将最新的关注关系添加进去
                    luaResult = redisTemplate.execute(redisScript, Collections.singletonList(userFollowingRedisKey), followUserId, timestamp);
                    checkLuaScriptResult(luaResult);
                }
            }
        }

        // 发送 MQ
        // 构建消息体
        FollowUserMqDTO followUserMqDTO = FollowUserMqDTO.builder()
                .followUserId(followUserId)
                .userId(userId)
                .createTime(now)
                .build();
        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(followUserMqDTO)).build();
        // 通过冒号链接，可让RocketMQ发送给Topic时，接待标签
        String destination = MQConstant.TOPIC_FOLLOW_OR_UNFOLLOW + ":" + MQConstant.TAG_FOLLOW;

        log.info("开始发送关注操作MQ：消息体：{}", followUserMqDTO);
        // 异步发送 MQ 消息，提升接口响应速度
        rocketMQTemplate.asyncSend(destination, message, new SendCallback() {

            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> MQ 发送异常: ", throwable);
            }
        });
        return Response.success();
    }

    @Override
    public Response<?> unfollow(UnfollowUserReqVO unfollowUserReqVO) {
        // 想要取关的用户的id
        Long unfollowUserId = unfollowUserReqVO.getUnfollowUserId();
        // 当前登录用户的id
        Long userId = LoginUserContextHolder.getUserId();
        // 无法取关自己
        if (Objects.equals(userId, unfollowUserId)) {
            throw new BusinessException(ResponseCodeEnum.CANT_UNFOLLOW_YOUR_SELF);
        }
        // 校验关注的用户是否存在
        FindUserByIdRespDTO findUserByIdRespDTO = userRpcService.findById(unfollowUserId);
        if (Objects.isNull(findUserByIdRespDTO)) {
            throw new BusinessException(ResponseCodeEnum.FOLLOW_USER_NOT_EXISTED);
        }
        // 必须是关注了的用户，才能取关
        // 当前用户的关注列表 Redis Key
        String followingRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/unfollow_check_and_delete.lua")));
        // 返回值类型
        script.setResultType(Long.class);
        // 执行 Lua 脚本，拿到返回结果
        Long result = redisTemplate.execute(script, Collections.singletonList(followingRedisKey), unfollowUserId);
        // 校验 Lua 脚本执行结果
        // 取关的用户不在关注列表中
        if (Objects.equals(result, LuaResultEnum.NOT_FOLLOWED.getCode())) {
            throw new BusinessException(ResponseCodeEnum.NOT_FOLLOWED);
        }
        if (Objects.equals(result, LuaResultEnum.ZSET_NOT_EXIST.getCode())) { // ZSET 关注列表不存在
            // 从数据库查询当前用户的关注关系记录
            List<FollowingDO> followingDOList = followingDOMapper.selectByUserId(userId);
            // 随机过期时间
            // 保底1天+随机秒数
            long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
            // 若记录为空，则表示还未关注任何人，提示还未关注对方
            if (CollUtil.isEmpty(followingDOList)) {
                throw new BusinessException(ResponseCodeEnum.NOT_FOLLOWED);
            } else { // 若记录不为空，则将关注关系数据全量同步到 Redis 中，并设置过期时间；
                // 构建 Lua 参数
                Object[] luaArgs = buildLuaArgs(followingDOList, expireSeconds);
                // 执行lua脚本 批量同步关注关系数据到 Redis 中
                DefaultRedisScript<Long> script3 = new DefaultRedisScript<>();
                script3.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
                script3.setResultType(Long.class);
                redisTemplate.execute(script3, Collections.singletonList(followingRedisKey), luaArgs);
                // 再次调用上面的 Lua 脚本：unfollow_check_and_delete.lua , 将取关的用户删除
                result = redisTemplate.execute(script, Collections.singletonList(followingRedisKey), unfollowUserId);
                // 再次校验结果
                if (Objects.equals(result, LuaResultEnum.NOT_FOLLOWED.getCode())) {
                    throw new BusinessException(ResponseCodeEnum.NOT_FOLLOWED);
                }
            }
        }
        // 发送MQ操作数据库
        // 构建消息体 DTO
        UnfollowUserMqDTO unfollowUserMqDTO = UnfollowUserMqDTO.builder()
                .unfollowUserId(unfollowUserId)
                .userId(userId)
                .createTime(LocalDateTime.now())
                .build();
        // 构建消息对象，并将 DTO 转成 Json 字符串设置到消息体中
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(unfollowUserMqDTO)).build();
        String destination = MQConstant.TOPIC_FOLLOW_OR_UNFOLLOW + ":" + MQConstant.TAG_UNFOLLOW;
        log.info("==> 开始发送取关操作 MQ, 消息体: {}", unfollowUserMqDTO);
        // 异步发送 MQ 消息，提升接口响应速度
        rocketMQTemplate.asyncSend(destination, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> MQ 发送异常: ", throwable);
            }
        });
        return Response.success();
    }

    @Override
    public PageResponse<FindFollowingUserRespVO> findFollowingList(FindFollowingListReqVO findFollowingListReqVO) {
        // 想要查询的用户 ID
        Long userId = findFollowingListReqVO.getUserId();
        // 页码
        Integer pageNo = findFollowingListReqVO.getPageNo();
        // 先从 Redis 中查询
        String followingListRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);
        // 查询目标用户关注列表 ZSet 的总大小
        long total = redisTemplate.opsForZSet().zCard(followingListRedisKey);
        // 返参
        List<FindFollowingUserRespVO> findFollowingUserRespVOS = null;
        // 每页展示 10 条数据
        long limit = 10;
        if (total > 0) { // 缓存中有数据
            // 计算一共多少页
            long totalPage = PageResponse.getTotalPage(total, limit);
            // 请求的页码超出了总页数
            if (pageNo > totalPage) return PageResponse.success(null, pageNo, total);
            // 准备从 Red 10 个元素，计算偏移量is 中查询 ZSet 分页数据
            // 每页 10 个元素，计算偏移量
            long offset = PageResponse.getOffset(pageNo, limit);
            // 使用 ZREVRANGEBYSCORE 命令按 score 降序获取元素，同时使用 LIMIT 子句实现分页
            // 注意：这里使用了 Double.POSITIVE_INFINITY 和 Double.NEGATIVE_INFINITY 作为分数范围
            // 因为关注列表最多有 1000 个元素，这样可以确保获取到所有的元素
            Set<Object> followingUserIdsSet = redisTemplate.opsForZSet()
                    .reverseRangeByScore(followingListRedisKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, offset, limit);
            if (CollUtil.isNotEmpty(followingUserIdsSet)) {
                // 提取所有用户 ID 到集合中
                List<Long> userIds = followingUserIdsSet.stream().map(object -> Long.valueOf(object.toString())).toList();
                // 若不为空，DTO 转 VO
                findFollowingUserRespVOS = rpcUserServiceAndDTO2VO(userIds, findFollowingUserRespVOS);
            }
        } else {
            // 若 Redis 中没有数据，则从数据库查询
            // 先查询记录总量
            long count = followingDOMapper.selectCountByUserId(userId);
            // 计算一共多少页
            long totalPage = PageResponse.getTotalPage(count, limit);
            // 请求的页码超出了总页数
            if (pageNo > totalPage) return PageResponse.success(null, pageNo, count);
            // 偏移量
            long offset = PageResponse.getOffset(pageNo, limit);
            // 分页查询
            List<FollowingDO> followingDOS = followingDOMapper.selectPageListByUserId(userId, offset, limit);
            // 赋值真实的记录总数
            total = count;
            // 若不为空，DTO 转 VO
            if (CollUtil.isNotEmpty(followingDOS)) {
                // 提取所有关注用户 ID 到集合中
                List<Long> userIds = followingDOS.stream().map(FollowingDO::getFollowingUserId).toList();
                // RPC: 调用用户服务，并将 DTO 转换为 VO
                findFollowingUserRespVOS = rpcUserServiceAndDTO2VO(userIds, findFollowingUserRespVOS);
            }
            // TODO: 异步将关注列表全量同步到 Redis
            threadPoolTaskExecutor.submit(() -> syncFollowingList2Redis(userId));
        }
        return PageResponse.success(findFollowingUserRespVOS, pageNo, total);
    }

    @Override
    public PageResponse<FindFansUserRespVO> findFansList(FindFansListReqVO findFansListReqVO) {
        // 想要查询的用户 ID
        Long userId = findFansListReqVO.getUserId();
        // 页码
        Integer pageNo = findFansListReqVO.getPageNo();
        // 先从 Redis 中查询
        String userFansKey = RedisKeyConstants.buildUserFansKey(userId);
        // 查询目标用户粉丝列表 ZSet 的总大小
        Long total = redisTemplate.opsForZSet().zCard(userFansKey);
        // 返参
        List<FindFansUserRespVO> findFansUserRespVOS = null;
        // 每页展示 10 条数据
        long limit = 10;
        if (total > 0) {// 缓存中有数据
            // 计算一共多少页
            long totalPage = PageResponse.getTotalPage(total, limit);
            // 请求的页码超出了总页数
            if (pageNo > totalPage) return PageResponse.success(null, pageNo, total);
            // 准备从 Redis 中查询 ZSet 分页数据
            // 每页 10 个元素，计算偏移量
            long offset = PageResponse.getOffset(pageNo, limit);
            // 使用 ZREVRANGEBYSCORE 命令按 score 降序获取元素，同时使用 LIMIT 子句实现分页
            Set<Object> followingUserIdsSet = redisTemplate.opsForZSet()
                    .reverseRangeByScore(userFansKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, offset, limit);
            if (CollUtil.isNotEmpty(followingUserIdsSet)) {
                // 提取所有用户 ID 到集合中
                List<Long> userIds = followingUserIdsSet.stream().map(object -> Long.valueOf(object.toString())).toList();
                // RPC: 批量查询用户信息
                findFansUserRespVOS = rpcUserServiceAndCountServiceAndDTO2VO(userIds, findFansUserRespVOS);
            }
        } else { // redis中没有，需要从数据库中查找
            // 先查询记录总量
            total = fansDOMapper.selectCountByUserId(userId);
            // 计算一共多少页
            long totalPage = PageResponse.getTotalPage(total, limit);
            // 请求的页码超出了总页数（只允许查询前 500 页）
            if (pageNo > 500 || pageNo > totalPage) return PageResponse.success(null, pageNo, total);
            // 偏移量
            long offset = PageResponse.getOffset(pageNo, limit);
            // 分页查询
            List<FansDO> fansDOS = fansDOMapper.selectPageListByUserId(userId, offset, limit);
            if (CollUtil.isNotEmpty(fansDOS)) {
                List<Long> fansUserIds = fansDOS.stream().map(FansDO::getFansUserId).toList();
                // RPC: 调用用户服务、计数服务，并将 DTO 转换为 VO
                findFansUserRespVOS = rpcUserServiceAndCountServiceAndDTO2VO(fansUserIds, findFansUserRespVOS);
                // 异步将粉丝列表同步到 Redis（最多5000条）
                threadPoolTaskExecutor.submit(() -> syncFansList2Redis(userId));
            }
        }
        return PageResponse.success(findFansUserRespVOS, pageNo, total);
    }

    /**
     * 构建 Lua 脚本参数
     *
     * @param followingDOS
     * @param expireSeconds
     * @return
     */
    private static Object[] buildLuaArgs(List<FollowingDO> followingDOS, long expireSeconds) {
        int argsLength = followingDOS.size() * 2 + 1; // 每个关注关系有 2 个参数（score 和 value），再加一个过期时间
        Object[] luaArgs = new Object[argsLength];

        int i = 0;
        for (FollowingDO following : followingDOS) {
            luaArgs[i] = DateUtils.localDateTime2Timestamp(following.getCreateTime()); // 关注时间作为 score
            luaArgs[i + 1] = following.getFollowingUserId();          // 关注的用户 ID 作为 ZSet value
            i += 2;
        }

        luaArgs[argsLength - 1] = expireSeconds; // 最后一个参数是 ZSet 的过期时间
        return luaArgs;
    }

    /**
     * 校验 Lua 脚本结果，根据状态码抛出对应的业务异常
     *
     * @param result
     */
    private static void checkLuaScriptResult(Long result) {
        LuaResultEnum luaResultEnum = LuaResultEnum.valueOf(result);

        if (Objects.isNull(luaResultEnum)) throw new RuntimeException("Lua 返回结果错误");
        // 校验 Lua 脚本执行结果
        switch (luaResultEnum) {
            // 关注数已达到上限
            case FOLLOW_LIMIT -> throw new BusinessException(ResponseCodeEnum.FOLLOWING_COUNT_LIMIT);
            // 已经关注了该用户
            case ALREADY_FOLLOWED -> throw new BusinessException(ResponseCodeEnum.ALREADY_FOLLOWED);
        }
    }

    /**
     * RPC: 调用用户服务，并将 DTO 转换为 VO
     *
     * @param userIds
     * @param findFollowingUserRespVOS
     * @return
     */
    private List<FindFollowingUserRespVO> rpcUserServiceAndDTO2VO(List<Long> userIds, List<FindFollowingUserRespVO> findFollowingUserRespVOS) {
        // RPC: 批量查询用户信息
        List<FindUserByIdRespDTO> findUserByIdRespDTOS = userRpcService.findByIds(userIds);

        // 若不为空，DTO 转 VO
        if (CollUtil.isNotEmpty(findUserByIdRespDTOS)) {
            findFollowingUserRespVOS = findUserByIdRespDTOS.stream()
                    .map(dto -> FindFollowingUserRespVO.builder()
                            .userId(dto.getId())
                            .avatar(dto.getAvatar())
                            .nickname(dto.getNickName())
                            .introduction(dto.getIntroduction())
                            .build())
                    .toList();
        }
        return findFollowingUserRespVOS;
    }

    /**
     * 全量同步关注列表至 Redis 中
     */
    private void syncFollowingList2Redis(Long userId) {
        // 查询全量关注用户列表（1000位用户）
        List<FollowingDO> followingDOS = followingDOMapper.selectAllByUserId(userId);
        if (CollUtil.isNotEmpty(followingDOS)) {
            // 用户关注列表 Redis Key
            String followingListRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);
            // 随机过期时间
            // 保底1天+随机秒数
            long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
            // 构建 Lua 参数
            Object[] luaArgs = buildLuaArgs(followingDOS, expireSeconds);

            // 执行 Lua 脚本，批量同步关注关系数据到 Redis 中
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
            script.setResultType(Long.class);
            redisTemplate.execute(script, Collections.singletonList(followingListRedisKey), luaArgs);
        }
    }

    /**
     * RPC: 调用用户服务、计数服务，并将 DTO 转换为 VO 粉丝列表
     *
     * @param userIds
     * @param findFansUserRespVOS
     * @return
     */
    private List<FindFansUserRespVO> rpcUserServiceAndCountServiceAndDTO2VO(List<Long> userIds, List<FindFansUserRespVO> findFansUserRespVOS) {
        // RPC: 批量查询用户信息
        List<FindUserByIdRespDTO> findUserByIdRspDTOS = userRpcService.findByIds(userIds);

        // TODO RPC: 批量查询用户的计数数据（笔记总数、粉丝总数）

        // 若不为空，DTO 转 VO
        if (CollUtil.isNotEmpty(findUserByIdRspDTOS)) {
            findFansUserRespVOS = findUserByIdRspDTOS.stream()
                    .map(dto -> FindFansUserRespVO.builder()
                            .userId(dto.getId())
                            .avatar(dto.getAvatar())
                            .nickname(dto.getNickName())
                            .noteTotal(0L) // TODO: 这块的数据暂无，后续补充
                            .fansTotal(0L) // TODO: 这块的数据暂无，后续补充
                            .build())
                    .toList();
        }
        return findFansUserRespVOS;
    }

    /**
     * 粉丝列表同步到 Redis（最多5000条）
     *
     * @param userId
     */
    private void syncFansList2Redis(Long userId) {
        // TODO
        // 查询粉丝列表（最多5000位用户）
        List<FansDO> fansDOS = fansDOMapper.select5000FansByUserId(userId);
        if (CollUtil.isNotEmpty(fansDOS)) {
            // 用户粉丝列表 Redis Key
            String fansListRedisKey = RedisKeyConstants.buildUserFansKey(userId);
            // 随机过期时间
            // 保底1天+随机秒数
            long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
            // 构建 Lua 参数
            Object[] luaArgs = buildFansZSetLuaArgs(fansDOS, expireSeconds);

            // 执行 Lua 脚本，批量同步关注关系数据到 Redis 中
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
            script.setResultType(Long.class);
            redisTemplate.execute(script, Collections.singletonList(fansListRedisKey), luaArgs);
        }
    }

    /**
     * 构建 Lua 脚本参数：粉丝列表
     * @param fansDOS
     * @param expireSeconds
     * @return
     */
    private static Object[] buildFansZSetLuaArgs(List<FansDO> fansDOS, long expireSeconds) {
        int argsLength = fansDOS.size() * 2 + 1; // 每个粉丝关系有 2 个参数（score 和 value），再加一个过期时间
        Object[] luaArgs = new Object[argsLength];

        int i = 0;
        for (FansDO fansDO : fansDOS) {
            luaArgs[i] = DateUtils.localDateTime2Timestamp(fansDO.getCreateTime()); // 粉丝的关注时间作为 score
            luaArgs[i + 1] = fansDO.getFansUserId();          // 粉丝的用户 ID 作为 ZSet value
            i += 2;
        }

        luaArgs[argsLength - 1] = expireSeconds; // 最后一个参数是 ZSet 的过期时间
        return luaArgs;
    }


}
