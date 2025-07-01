package com.danby.happynode.kv.biz.service.impl;

import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.kv.biz.domain.dataobject.CommentContentDO;
import com.danby.happynode.kv.biz.domain.dataobject.CommentContentPrimaryKey;
import com.danby.happynode.kv.biz.service.CommentContentService;
import com.danby.happynode.kv.dto.req.BatchAddCommentContentReqDTO;
import com.danby.happynode.kv.dto.req.CommentContentReqDTO;
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
}
