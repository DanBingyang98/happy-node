package com.danby.happynode.search.service.impl;

import com.danby.happynode.framework.common.response.PageResponse;
import com.danby.happynode.search.index.UserIndex;
import com.danby.happynode.search.model.vo.SearchUserReqVO;
import com.danby.happynode.search.model.vo.SearchUserRespVO;
import com.danby.happynode.search.service.UserService;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
    @Autowired
    private RestHighLevelClient restHighLevelClient;

    /**
     * 搜索用户
     *
     * @param searchUserReqVO
     * @return
     */
    @Override
    public PageResponse<SearchUserRespVO> searchUser(SearchUserReqVO searchUserReqVO) {
/*      GET /user/_search
        {
          "query": {
            "multi_match": {
              "query": "abcc",
              "fields": ["nickname", "happynode_id"]
            }
          },
            "sort": [
            {
              "fans_total": {
                "order": "desc"
              }
            }
          ],
          "from": 0,
          "size": 10
        }
*/
        // 解析请求参数
        String keyword = searchUserReqVO.getKeyword();
        Integer pageNo = searchUserReqVO.getPageNo();

        // 1 构建 SearchRequest，指定索引
        SearchRequest searchRequest = new SearchRequest(UserIndex.NAME);
        // 1.1 构建查询内容
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        // 1.2 构建 multi_match 查询，查询 nickname 和 xiaohashu_id 字段
        searchSourceBuilder.query(QueryBuilders.multiMatchQuery(
                keyword, UserIndex.FIELD_USER_NICKNAME, UserIndex.FIELD_USER_HAPPYNODE_ID));
        // 1.3 构建排序，按 fans_total 降序
        FieldSortBuilder sortBuilder = new FieldSortBuilder(UserIndex.FIELD_USER_FANS_TOTAL)
                .order(SortOrder.DESC);
        // 1.4 添加排序
        searchSourceBuilder.sort(sortBuilder);
        // 1.5 设置分页 from size
        int pageSize = 10; // 每页展示数据量
        int from = (pageNo - 1) / pageSize; // 偏移量
        // 将排序信息添加到 SearchRequest
        searchSourceBuilder.from(from);
        searchSourceBuilder.size(pageSize);
        // 1.6 将构建的查询条件设置到 SearchRequest 中
        searchRequest.source(searchSourceBuilder);
        // 2 定义查询结果
        List<SearchUserRespVO> searchUserRespVOS = null;
        // 总文档数量
        long total = 0;
        try {
            // 3 执行查询
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            // 4 解析查询结果
            // 4.1 获取总文档数量
            total = searchResponse.getHits().getTotalHits().value;
            log.info("==> 命中文档总数, hits: {}", total);
            // 4.2 获取文档内容hits
            SearchHits hits = searchResponse.getHits();
            searchUserRespVOS = Lists.newArrayList();
            // 4.3 遍历 hits
            for (SearchHit hit : hits) {
                // 拿到hit的sourceMap
                Map<String, Object> sourceAsMap = hit.getSourceAsMap();
                // 提取特定字段值
                Long userId = ((Number) sourceAsMap.get(UserIndex.FIELD_USER_ID)).longValue();
                String nickname = (String) sourceAsMap.get(UserIndex.FIELD_USER_NICKNAME);
                String avatar = (String) sourceAsMap.get(UserIndex.FIELD_USER_AVATAR);
                String happynodeId = (String) sourceAsMap.get(UserIndex.FIELD_USER_HAPPYNODE_ID);
                Integer noteTotal = (Integer) sourceAsMap.get(UserIndex.FIELD_USER_NOTE_TOTAL);
                Integer fansTotal = (Integer) sourceAsMap.get(UserIndex.FIELD_USER_FANS_TOTAL);
                // 构建 VO 实体类
                SearchUserRespVO searchUserRespVO = SearchUserRespVO.builder()
                        .userId(userId)
                        .nickname(nickname)
                        .avatar(avatar)
                        .fansTotal(fansTotal)
                        .happynodeId(happynodeId)
                        .noteTotal(noteTotal)
                        .build();
                searchUserRespVOS.add(searchUserRespVO);
            }
        } catch (Exception e) {
            log.error("==> 查询 Elasticserach 异常: ", e);
        }

        return PageResponse.success(searchUserRespVOS, pageNo, total);
    }
}
