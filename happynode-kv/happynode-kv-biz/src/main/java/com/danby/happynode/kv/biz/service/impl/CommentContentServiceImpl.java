package com.danby.happynode.kv.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.kv.biz.domain.dataobject.CommentContentDO;
import com.danby.happynode.kv.biz.domain.dataobject.CommentContentPrimaryKey;
import com.danby.happynode.kv.biz.domain.repository.CommentContentRepository;
import com.danby.happynode.kv.biz.service.CommentContentService;
import com.danby.happynode.kv.dto.req.*;
import com.danby.happynode.kv.dto.resp.FindCommentContentRespDTO;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class CommentContentServiceImpl implements CommentContentService {

    @Autowired
    private CassandraTemplate cassandraTemplate;

    @Autowired
    private CommentContentRepository commentContentRepository;

    /**
     * 批量添加评论内容
     *
     * @param batchAddCommentContentReqDTO
     * @return
     */
    @Override
    public Response<?> batchAddCommentContent(BatchAddCommentContentReqDTO batchAddCommentContentReqDTO) {
        List<CommentContentReqDTO> comments = batchAddCommentContentReqDTO.getComments();
        List<CommentContentDO> commentContentDOS = comments.stream().map(comment -> {
            CommentContentPrimaryKey commentContentPrimaryKey = CommentContentPrimaryKey.builder()
                    .contentId(UUID.fromString(comment.getContentId()))
                    .noteId(comment.getNoteId())
                    .yearMonth(comment.getYearMonth())
                    .build();

            return CommentContentDO.builder()
                    .primaryKey(commentContentPrimaryKey)
                    .content(comment.getContent())
                    .build();
        }).toList();
        // 批量插入
        cassandraTemplate.batchOps().insert(commentContentDOS).execute();
        return Response.success();
    }

    /**
     * 批量查找评论内容
     *
     * @param batchFindCommentContentReqDTO 批量查找评论内容请求参数，包含笔记ID和评论内容键列表
     * @return Response<?> 响应回复，数据部分包含查找到的评论内容响应DTO列表
     */
    @Override
    public Response<?> batchFindCommentContent(BatchFindCommentContentReqDTO batchFindCommentContentReqDTO) {
        Long noteId = batchFindCommentContentReqDTO.getNoteId();
        List<FindCommentContentReqDTO> commentContentKeys = batchFindCommentContentReqDTO.getCommentContentKeys();
        List<String> yearMonth = commentContentKeys.stream()
                .map(FindCommentContentReqDTO::getYearMonth)
                .distinct()
                .toList();
        List<UUID> contentIds = commentContentKeys.stream()
                .map(FindCommentContentReqDTO::getContentId)
                .map(UUID::fromString)
                .distinct()
                .toList();
        // 提取唯一的内容ID年月标识和UUID，用于查询评论内容
        List<CommentContentDO> results = commentContentRepository.findByPrimaryKeyNoteIdAndPrimaryKeyYearMonthInAndPrimaryKeyContentIdIn(
                noteId, yearMonth, contentIds
        );
        // 根据笔记ID、年月标识和内容ID批量查询评论内容
        List<FindCommentContentRespDTO> findCommentContentRspDTOS = Lists.newArrayList();
        if (CollUtil.isNotEmpty(results)) {
            findCommentContentRspDTOS = results.stream()
                    .map(result ->
                            FindCommentContentRespDTO.builder()
                                    .content(result.getContent())
                                    .contentId(result.getPrimaryKey().getContentId().toString())
                                    .build())
                    .toList();
        }
        // 返回成功响应及查找到的评论内容
        return Response.success(findCommentContentRspDTOS);
    }

    /**
     * 删除评论内容
     *
     * @param deleteCommentContentReqDTO
     * @return
     */
    @Override
    public Response<?> deleteCommentContent(DeleteCommentContentReqDTO deleteCommentContentReqDTO) {
        Long noteId = deleteCommentContentReqDTO.getNoteId();
        String yearMonth = deleteCommentContentReqDTO.getYearMonth();
        String contentId = deleteCommentContentReqDTO.getContentId();
        commentContentRepository.deleteByPrimaryKeyNoteIdAndPrimaryKeyYearMonthAndPrimaryKeyContentId(noteId, yearMonth, UUID.fromString(contentId));
        return Response.success();
    }
}

