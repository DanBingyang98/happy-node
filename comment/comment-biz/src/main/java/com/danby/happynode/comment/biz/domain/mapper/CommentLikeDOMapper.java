package com.danby.happynode.comment.biz.domain.mapper;

import com.danby.happynode.comment.biz.domain.dataobject.CommentLikeDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CommentLikeDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(CommentLikeDO record);

    int insertSelective(CommentLikeDO record);

    CommentLikeDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(CommentLikeDO record);

    int updateByPrimaryKey(CommentLikeDO record);

    /**
     * 查询某个评论是否被点赞
     *
     * @param userId
     * @param commentId
     * @return
     */
    int selectCountByUserIdAndCommentId(@Param("userId") Long userId,
                                        @Param("commentId") Long commentId);

    List<CommentLikeDO> selectByUserId(@Param("userId") Long userId);
}