package com.danby.happynode.comment.biz.domain.mapper;

import com.danby.happynode.comment.biz.domain.dataobject.CommentDO;
import com.danby.happynode.comment.biz.model.bo.CommentBO;
import com.danby.happynode.comment.biz.model.bo.CommentHeatBO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CommentDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(CommentDO record);

    int insertSelective(CommentDO record);

    CommentDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(CommentDO record);

    int updateByPrimaryKey(CommentDO record);

    /**
     * 根据评论 ID 批量查询
     * @param commentIds
     * @return
     */
    List<CommentDO> selectByCommentIds(@Param("commentIds") List<Long> commentIds);

    /**
     * 批量插入评论
     * @param comments
     * @return
     */
    int batchAddComment(@Param("comments")List<CommentBO> comments);

    /**
     * 批量更新热度值
     * @param commentIds
     * @param commentHeatBOS
     * @return
     */
    int batchUpdateHeatByCommentIds(@Param("commentIds") List<Long> commentIds,
                                    @Param("commentHeatBOS") List<CommentHeatBO> commentHeatBOS);
}