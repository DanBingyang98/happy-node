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
public class NoteCollectCountShardingXxlJob {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private UpdateMapper updateMapper;
    @Autowired
    private SelectMapper selectMapper;
    @Autowired
    private DeleteMapper deleteMapper;

    @XxlJob("noteCollectCountShardingJobHandler")
    public void noteCollectCountShardingJobHandler() throws Exception {
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
        // 1. 分批次查询 t_data_align_note_collect_count_temp_日期_分片序号，如一批次查询 1000 条，直到全部查询完成
        // 2. 循环这一批发生变更的用户 ID， 对 t_fans 关注表执行 count(*) 操作，获取总数
        // 3. 批量更新 t_note_count 表，并更新对应 Redis 缓存
        // 4. 批量物理删除这一批次记录
        while (true) {
            // 1. 分批次查询 t_data_align_note_collect_count_temp_日期_分片序号，如一批次查询 1000 条，直到全部查询完成
            // 批量查询
            List<Long> noteIdList = selectMapper.selectBatchFromDataAlignNoteCollectCountTempTable(tableNameSuffix, batchSize);
            if (CollUtil.isEmpty(noteIdList)) break;
            noteIdList.forEach(noteId -> {
                // 2. 循环这一批发生变更的用户 ID， 对 t_fans 关注表执行 count(*) 操作，获取总数
                int collectTotal = selectMapper.selectCountFromNoteCollectTableByNoteId(noteId);
                // 3. 批量更新 t_note_count 表，并更新对应 Redis 缓存
                int count = updateMapper.updateNoteCollectTotalByNoteId(noteId, collectTotal);
                if (count > 0) {
                    String countNoteKey = RedisKeyConstants.buildCountNoteKey(noteId);
                    Boolean hasKey = redisTemplate.hasKey(countNoteKey);
                    if (hasKey) {
                        redisTemplate.opsForHash().put(countNoteKey, RedisKeyConstants.FIELD_COLLECT_TOTAL, collectTotal);
                    }
                }
            });
            // 4. 批量物理删除这一批次记录
            deleteMapper.batchDeleteDataAlignNoteCollectCountTempTable(tableNameSuffix, noteIdList);
            // 当前已处理的记录数
            processedTotal += noteIdList.size();
        }
        XxlJobHelper.log("=================> 结束定时分片广播任务：对当日发生变更的笔记点赞数进行对齐，共对齐记录数：{}", processedTotal);
    }
}
