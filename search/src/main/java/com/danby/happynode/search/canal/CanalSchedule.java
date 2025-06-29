package com.danby.happynode.search.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.danby.happynode.framework.common.enums.StatusEnum;
import com.danby.happynode.search.domain.mapper.SelectMapper;
import com.danby.happynode.search.enums.NoteStatusEnum;
import com.danby.happynode.search.enums.NoteVisibleEnum;
import com.danby.happynode.search.index.NoteIndex;
import com.danby.happynode.search.index.UserIndex;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class CanalSchedule implements Runnable {

    @Autowired
    private CanalProperties canalProperties;

    @Autowired
    private CanalConnector canalConnector;

    @Autowired
    private SelectMapper selectMapper;

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Override
    @Scheduled(fixedDelay = 100) // 每隔 100ms 被执行一次
    public void run() {
        // 初始化批次 ID，-1 表示未开始或未获取到数据
        long batchId = -1;
        try {
            // 从 canalConnector 获取批量消息，返回的数据量由 batchSize 控制，若不足，则拉取已有的
            Message message = canalConnector.getWithoutAck(canalProperties.getBatchSize());

            // 获取当前拉取消息的批次 ID
            batchId = message.getId();

            // 获取当前批次中的数据条数
            long size = message.getEntries().size();
            if (batchId == -1 || size == 0) {
                try {
                    // 拉取数据为空，休眠 1s, 防止频繁拉取
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                }
            } else {
                // 如果当前批次有数据，打印这批次中的数据条目
                processEntry(message.getEntries());
            }

            // 对当前批次的消息进行 ack 确认，表示该批次的数据已经被成功消费
            canalConnector.ack(batchId);
        } catch (Exception e) {
            log.error("消费 Canal 批次数据异常", e);
            // 如果出现异常，需要进行数据回滚，以便重新消费这批次的数据
            canalConnector.rollback(batchId);
        }
    }

    /**
     * 处理并解析Canal日志条目，将数据变更信息打印到控制台
     *
     * @param entrys Canal日志条目列表，包含数据库变更记录
     * @return 无返回值，处理结果直接打印输出
     */
    private void processEntry(List<CanalEntry.Entry> entrys) throws Exception {
        // 遍历处理每个Canal日志条目
        for (CanalEntry.Entry entry : entrys) {
            // 只处理 ROWDATA 行数据类型的 Entry，忽略事务等其他类型
            if (entry.getEntryType() == CanalEntry.EntryType.ROWDATA) {
                // 获取事件类型 （如：INSERT、UPDATE、DELETE 等等）
                CanalEntry.EventType eventType = entry.getHeader().getEventType();
                // 获取数据库名称
                String database = entry.getHeader().getSchemaName();
                // 获取表名称
                String tableName = entry.getHeader().getTableName();
                // 解析出 RowChange 对象，包含 RowData 和事件相关信息
                CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
                for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                    // 获取行中所有列的最新值（AfterColumns）
                    List<CanalEntry.Column> columns = rowData.getAfterColumnsList();
                    // 将列数据解析为 Map，方便后续处理
                    Map<String, Object> columnMap = parseColumns2Map(columns);
                    // 自定义处理
                    log.info("EventType: {}, Database: {}, Table: {}, Columns: {}", eventType, database, tableName, columnMap);
                    // 处理事件
                    processEvent(columnMap, tableName, eventType);

                }
            }
        }
    }

    /**
     * 打印字段信息
     *
     * @param columns
     */
    private static Map<String, Object> parseColumns2Map(List<CanalEntry.Column> columns) {
        HashMap<String, Object> resultMap = Maps.newHashMap();
        columns.forEach(column -> {
            if (Objects.isNull(column)) return;
            resultMap.put(column.getName(), column.getValue());
        });
        return resultMap;
    }

    /**
     * 处理事件
     *
     * @param columnMap
     * @param table
     * @param eventType
     */
    private void processEvent(Map<String, Object> columnMap, String table, CanalEntry.EventType eventType) throws Exception {
        switch (table) {
            case "t_note" -> handleNoteEvent(columnMap, eventType); // 笔记表
            case "t_user" -> handleUserEvent(columnMap, eventType); // 用户表
            default -> log.warn("Table: {} not support", table);
        }
    }

    /**
     * 处理笔记表事件
     *
     * @param columnMap
     * @param eventType
     */
    private void handleUserEvent(Map<String, Object> columnMap, CanalEntry.EventType eventType) throws Exception {
        // 获取用户 ID
        long userId = Long.parseLong(columnMap.get("id").toString());
        switch (eventType) {
            case INSERT -> syncUserIndex(userId); // 记录新增事件
            case UPDATE -> { // 记录更新事件
                // 用户变更后的状态
                Integer status = Integer.parseInt(columnMap.get("status").toString());
                // 逻辑删除
                Integer isDeleted = Integer.parseInt(columnMap.get("is_deleted").toString());
                if (Objects.equals(status, StatusEnum.ENABLED.getValue())
                        && Objects.equals(isDeleted, 0)) {  // 用户状态为已启用，并且未被逻辑删除
                    syncNotesIndexAndUserIndex(userId); // 更新用户索引、笔记索引
                } else if (Objects.equals(status, StatusEnum.DISABLED.getValue()) // 用户状态为禁用
                        || Objects.equals(isDeleted, 1)) { // 被逻辑删除
                    // 删除用户文档
                    deleteUserDocument(String.valueOf(userId));
                }

            }
            default -> log.warn("Unhandled event type for t_user: {}", eventType);
        }
    }

    /**
     * 删除指定 ID 的用户文档
     *
     * @param documentId
     * @throws Exception
     */
    private void deleteUserDocument(String documentId) throws Exception {
        // 创建删除请求对象，指定索引名称和文档 ID
        DeleteRequest deleteRequest = new DeleteRequest(UserIndex.NAME, documentId);
        // 执行删除操作，将指定文档从 Elasticsearch 索引中删除
        restHighLevelClient.delete(deleteRequest, RequestOptions.DEFAULT);
    }

    private void syncNotesIndexAndUserIndex(long userId) throws Exception {
        BulkRequest bulkRequest = new BulkRequest();

        // 1 同步用户索引
        List<Map<String, Object>> userMaps = selectMapper.selectEsUserIndexData(userId);
        for (Map<String, Object> map : userMaps) {
            IndexRequest indexRequest = new IndexRequest(UserIndex.NAME);
            indexRequest.id(String.valueOf(map.get(UserIndex.FIELD_USER_ID)));
            indexRequest.source(map);
            bulkRequest.add(indexRequest);
        }
        // 2 同步笔记索引
        List<Map<String, Object>> noteMaps = selectMapper.selectEsNoteIndexData(null, userId);
        for (Map<String, Object> noteMap : noteMaps) {
            IndexRequest indexRequest = new IndexRequest(NoteIndex.NAME);
            indexRequest.id(String.valueOf(noteMap.get(NoteIndex.FIELD_NOTE_ID)));
            indexRequest.source(noteMap);
            bulkRequest.add(indexRequest);
        }

        // 3 执行批量请求
        restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT);

    }

    /**
     * 同步用户索引
     *
     * @param userId
     */
    private void syncUserIndex(long userId) throws Exception {
        // 1. 同步用户索引
        List<Map<String, Object>> userMaps = selectMapper.selectEsUserIndexData(userId);
        // 遍历查询结果，将每条记录同步到 Elasticsearch
        for (Map<String, Object> userMap : userMaps) {
            // 创建索引请求对象，指定索引名称
            IndexRequest indexRequest = new IndexRequest(UserIndex.NAME);
            // 设置文档的 ID，使用记录中的主键 “id” 字段值s
            indexRequest.id(String.valueOf(userMap.get(UserIndex.FIELD_USER_ID)));
            // 设置文档的内容，使用查询结果的记录数据
            indexRequest.source(userMap);
            // 将数据写入 Elasticsearch 索引
            restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
        }
    }

    /**
     * 处理用户表事件
     *
     * @param columnMap
     * @param eventType
     */
    private void handleNoteEvent(Map<String, Object> columnMap, CanalEntry.EventType eventType) throws Exception {
        // 获取笔记 ID
        Long noteId = Long.parseLong(columnMap.get("id").toString());
        // 不同的事件，处理逻辑不同
        switch (eventType) {
            case INSERT -> syncNoteIndex(noteId); // 记录新增事件
            case UPDATE -> { // 记录更新事件
                // 笔记变更后的状态
                Integer status = Integer.parseInt(columnMap.get("status").toString());
                // 笔记可见范围
                Integer visible = Integer.parseInt(columnMap.get("visible").toString());
                if (Objects.equals(status, NoteStatusEnum.NORMAL.getCode()) // 正常展示
                        && Objects.equals(visible, NoteVisibleEnum.PUBLIC.getCode())) { // 可见性为公开
                    syncNoteIndex(noteId);
                } else if (Objects.equals(visible, NoteVisibleEnum.PRIVATE.getCode()) // 仅对自己可见
                        || Objects.equals(status, NoteStatusEnum.DELETED.getCode()) // 被逻辑删除
                        || Objects.equals(status, NoteStatusEnum.DOWNED.getCode())) { // 被下架
                    //删除文档
                    deleteNoteDocument(noteId);
                }
            }
            default -> log.warn("Unhandled event type for t_note: {}", eventType);
        }
    }

    /**
     * 同步笔记索引
     *
     * @param noteId
     * @throws Exception
     */
    private void syncNoteIndex(Long noteId) throws Exception {
        // 从数据库查询 Elasticsearch 索引数据
        List<Map<String, Object>> maps = selectMapper.selectEsNoteIndexData(noteId, null);
        // 遍历查询结果，将每条记录同步到 Elasticsearch
        for (Map<String, Object> resultMap : maps) {
            // 创建索引请求对象，指定索引名称
            IndexRequest indexRequest = new IndexRequest(NoteIndex.NAME);
            // 设置文档的 ID，使用记录中的主键 “id” 字段值
            indexRequest.id(String.valueOf(resultMap.get(NoteIndex.FIELD_NOTE_ID)));
            // 设置文档的内容，使用查询结果的记录数据
            indexRequest.source(resultMap);
            // 将数据写入 Elasticsearch 索引
            restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
        }
    }

    /**
     * 删除指定 ID 的文档
     *
     * @param documentId
     * @throws Exception
     */
    private void deleteNoteDocument(Long documentId) throws Exception {
        // 创建删除请求对象，指定索引名称和文档 ID
        DeleteRequest deleteRequest = new DeleteRequest(NoteIndex.NAME, String.valueOf(documentId));
        // 执行删除操作，将指定文档从 Elasticsearch 索引中删除
        restHighLevelClient.delete(deleteRequest, RequestOptions.DEFAULT);
    }


}
