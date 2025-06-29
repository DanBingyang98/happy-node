package com.danby.happynode.data.align.job;

import cn.hutool.core.collection.CollUtil;
import com.danby.happynode.data.align.constant.RedisKeyConstants;
import com.danby.happynode.data.align.constant.TableConstants;
import com.danby.happynode.data.align.domain.mapper.DeleteMapper;
import com.danby.happynode.data.align.domain.mapper.SelectMapper;
import com.danby.happynode.data.align.domain.mapper.UpdateMapper;
import com.danby.happynode.data.align.rpc.SearchRpcService;
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
public class NotePublishCountShardingXxlJob {

    @Autowired
    private SelectMapper selectMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private DeleteMapper deleteMapper;
    @Autowired
    private UpdateMapper updateMapper;
    @Autowired
    private SearchRpcService searchRpcService;

    @XxlJob("notePublishCountShardingHandler")
    public void notePublishCountShardingHandler() {
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
        while (true) {
            List<Long> userIds = selectMapper.selectBatchFromDataAlignNotePublishCountTempTable(tableNameSuffix, batchSize);
            if (CollUtil.isEmpty(userIds)) break;
            userIds.forEach(userId -> {
                int noteTotal = selectMapper.selectCountFromNoteTableByUserId(userId);
                int count = updateMapper.updateUserNoteTotalByUserId(userId, noteTotal);
                if (count > 0) {
                    String countUserKey = RedisKeyConstants.buildCountUserKey(userId);
                    Boolean hasKey = redisTemplate.hasKey(countUserKey);
                    if (hasKey) {
                        // 更新对应 Redis 缓存
                        redisTemplate.opsForHash().put(countUserKey, RedisKeyConstants.FIELD_NOTE_TOTAL, noteTotal);
                    }
                }
                // 笔记文档重建
                searchRpcService.rebuildUserDocument(userId);
            });
            deleteMapper.batchDeleteDataAlignNotePublishCountTempTable(tableNameSuffix, userIds);
            // 当前已处理的记录数
            processedTotal += userIds.size();
        }
        XxlJobHelper.log("=================> 开始定时分片广播任务：对当日发生变更的用户发布笔记数进行对齐，共对齐记录数：{}", processedTotal);
    }
}
