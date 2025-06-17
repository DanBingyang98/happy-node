package com.danby.happynode.user.relation.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.danby.framework.context.holder.LoginUserContextHolder;
import com.danby.happynode.framework.common.exception.BusinessException;
import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.framework.common.util.DateUtils;
import com.danby.happynode.framework.common.util.JsonUtils;
import com.danby.happynode.user.dto.resp.FindUserByIdRespDTO;
import com.danby.happynode.user.relation.biz.constant.MQConstant;
import com.danby.happynode.user.relation.biz.constant.RedisKeyConstants;
import com.danby.happynode.user.relation.biz.domain.dataobject.FollowingDO;
import com.danby.happynode.user.relation.biz.domain.mapper.FollowingDOMapper;
import com.danby.happynode.user.relation.biz.enums.LuaResultEnum;
import com.danby.happynode.user.relation.biz.enums.ResponseCodeEnum;
import com.danby.happynode.user.relation.biz.model.dto.FollowUserMqDTO;
import com.danby.happynode.user.relation.biz.model.vo.FollowUserReqVO;
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
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

        // TODO: 发送 MQ
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
}
