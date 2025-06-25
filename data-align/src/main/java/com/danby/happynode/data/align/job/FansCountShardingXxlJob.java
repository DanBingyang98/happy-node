package com.danby.happynode.data.align.job;

import cn.hutool.core.collection.CollUtil;
import com.danby.happynode.data.align.constant.RedisKeyConstants;
import com.danby.happynode.data.align.constant.TableConstants;
import com.danby.happynode.data.align.domain.mapper.DeleteMapper;
import com.danby.happynode.data.align.domain.mapper.SelectMapper;
import com.danby.happynode.data.align.domain.mapper.UpdateMapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Slf4j
public class FansCountShardingXxlJob {

    @Autowired
    private SelectMapper selectMapper;

    @Autowired
    private UpdateMapper updateMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private DeleteMapper deleteMapper;

    @XxlJob("fansCountShardingJobHandler")
    public void fansCountShardingJobHandler() throws Exception {// 获取分片参数
        // 分片序号
        int shardIndex = XxlJobHelper.getShardIndex();
        // 分片总数
        int shardTotal = XxlJobHelper.getShardTotal();

        XxlJobHelper.log("分片参数：当前分片序号 = {}, 总分片数 = {}", shardIndex, shardTotal);
        log.info("分片参数：当前分片序号 = {}, 总分片数 = {}", shardIndex, shardTotal);
        // 表后缀
        String date = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String tableNameSuffix = TableConstants.buildTableNameSuffix(date, shardIndex);

        // 业务逻辑
        // 1. 分批次查询 t_data_align_fans_count_temp_日期_分片序号，如一批次查询 1000 条，直到全部查询完成
        // 2. 循环这一批发生变更的用户 ID， 对 t_fans 关注表执行 count(*) 操作，获取总数
        // 3. 批量更新 t_user_count 表，并更新对应 Redis 缓存
        // 4. 批量物理删除这一批次记录

        // 1. 分批次查询 t_data_align_fans_count_temp_日期_分片序号，如一批次查询 1000 条，直到全部查询完成
        int batchSize = 1000;
        int processedTotal = 0;
        while (true) {
            List<Long> userIds = selectMapper.selectBatchFromDataAlignFansCountTempTable(tableNameSuffix, batchSize);
            if (CollUtil.isEmpty(userIds)) break;
            //2. 循环这一批发生变更的用户 ID， 对 t_fans 关注表执行 count(*) 操作，获取总数
            userIds.forEach(userId -> {
                // 3. 批量更新 t_user_count 表，并更新对应 Redis 缓存
                int fansTotal = selectMapper.selectCountFromFansTableByUserId(userId);
                //更新 t_user_count 表
                int count = updateMapper.updateUserFansTotalByUserId(userId, fansTotal);
                if (count > 0) {
                    String countUserKey = RedisKeyConstants.buildCountUserKey(userId);
                    Boolean hasKey = redisTemplate.hasKey(countUserKey);
                    if (hasKey) {
                        // 更新对应 Redis 缓存
                        redisTemplate.opsForHash().put(countUserKey, RedisKeyConstants.FIELD_FANS_TOTAL, fansTotal);
                    }
                }
            });
            processedTotal += userIds.size();
            // 4. 批量物理删除这一批次记录
            deleteMapper.batchDeleteDataAlignFansCountTempTable(tableNameSuffix, userIds);
        }
        XxlJobHelper.log("=================> 结束定时分片广播任务：对当日发生变更的粉丝数进行对齐，共对齐记录数：{}", processedTotal);
    }
}
