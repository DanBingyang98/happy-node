package com.danby.happynode.comment.biz.rpc;

import com.danby.happynode.comment.biz.model.bo.CommentBO;
import com.danby.happynode.framework.common.constant.DateConstants;
import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.kv.dto.api.KeyValueFeign;
import com.danby.happynode.kv.dto.req.BatchAddCommentContentReqDTO;
import com.danby.happynode.kv.dto.req.CommentContentReqDTO;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class KeyValueRpcService {

    @Autowired
    private KeyValueFeign keyValueFeign;

    public Boolean batchAddCommentContent(List<CommentBO> commentBOs) {
        List<CommentContentReqDTO> commentContentReqDTOs = Lists.newArrayList();
        // BO 转 DTO
        commentBOs.forEach(commentBO -> {
            CommentContentReqDTO commentContentReqDTO = CommentContentReqDTO.builder()
                    .noteId(commentBO.getNoteId())
                    .contentId(commentBO.getContentUuid())
                    .yearMonth(commentBO.getCreateTime().format((DateConstants.DATE_FORMAT_Y_M)))
                    .content(commentBO.getContent())
                    .build();
            commentContentReqDTOs.add(commentContentReqDTO);
        });
        // 构建接口入参实体类
        BatchAddCommentContentReqDTO batchAddCommentContentReqDTO = BatchAddCommentContentReqDTO.builder()
                .comments(commentContentReqDTOs)
                .build();

        Response<?> response = keyValueFeign.batchAddCommentContent(batchAddCommentContentReqDTO);
        // 若返参中 success 为 false, 则主动抛出异常，以便调用层回滚事务
        if (!response.isSuccess()) {
            throw new RuntimeException("批量保存评论内容失败");
        }
        return true;


    }
}
