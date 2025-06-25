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
public class UserCollectCountShardingXxlJob {
    @Autowired
    private SelectMapper selectMapper;
    @Autowired
    private UpdateMapper updateMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private DeleteMapper deleteMapper;

    @XxlJob("userCollectCountShardingJobHandler")
    public void userCollectCountShardingJobHandler() throws Exception {
        // 获取分片参数
        // 分片序号
        int shardIndex = XxlJobHelper.getShardIndex();
        // 分片总数
        int shardTotal = XxlJobHelper.getShardTotal();

        XxlJobHelper.log("=================> 开始定时分片广播任务：对当日发生变更的笔记点赞数进行对齐");
        XxlJobHelper.log("分片参数：当前分片序号 = {}, 总分片数 = {}", shardIndex, shardTotal);

        log.info("分片参数：当前分片序号 = {}, 总分片数 = {}", shardIndex, shardTotal);
        // 表名后缀
        String date = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String tableNameSuffix = TableConstants.buildTableNameSuffix(date, shardIndex);
        // 一批次 1000 条
        int batchSize = 1000;
        // 共对齐了多少条记录，默认为 0
        int processedTotal = 0;
        // 业务逻辑
        // 1. 分批次查询 t_data_align_user_collect_count_temp_日期_分片序号，如一批次查询 1000 条，直到全部查询完成
        // 2. 循环这一批发生变更的用户 ID， 对 t_note_collection 关注表执行 count(*) 操作，获取总数
        // 3. 批量更新 t_user_count 表，并更新对应 Redis 缓存
        // 4. 批量物理删除这一批次记录
        while (true) {
            List<Long> userIds = selectMapper.selectBatchFromDataAlignUserCollectCountTempTable(tableNameSuffix, batchSize);
            if (CollUtil.isEmpty(userIds)) break;
            userIds.forEach(userId -> {
                int collectTotal = selectMapper.selectCountFromNoteCollectTableByUserId(userId);
                int count = updateMapper.updateUserCollectTotalByUserId(userId, collectTotal);
                if (count > 0) {
                    String countUserKey = RedisKeyConstants.buildCountUserKey(userId);
                    Boolean hasKey = redisTemplate.hasKey(countUserKey);
                    if (hasKey) {
                        redisTemplate.opsForHash().put(countUserKey, RedisKeyConstants.FIELD_COLLECT_TOTAL, collectTotal);
                    }
                }
            });
            deleteMapper.batchDeleteDataAlignUserCollectCountTempTable(tableNameSuffix, userIds);
            processedTotal += userIds.size();
        }
        XxlJobHelper.log("=================> 结束定时分片广播任务：对当日发生变更的用户收藏数进行对齐，共对齐记录数：{}", processedTotal);
    }
}
