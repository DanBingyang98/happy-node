package com.danby.happynode.count.biz.domain.mapper;

import com.danby.happynode.count.biz.domain.dataobject.NoteCountDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NoteCountDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(NoteCountDO record);

    int insertSelective(NoteCountDO record);

    NoteCountDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(NoteCountDO record);

    int updateByPrimaryKey(NoteCountDO record);

    /**
     * 添加笔记计数记录或更新笔记点赞数
     * @param count
     * @param noteId
     * @return
     */
    int insertOrUpdateLikeTotalByNoteId(@Param("count") Integer count, @Param("noteId") Long noteId);

    /**
     * 添加记录或更新笔记评论数
     * @param count
     * @param noteId
     * @return
     */
    int insertOrUpdateCommentTotalByNoteId(@Param("count") Integer count, @Param("noteId") Long noteId);

    /***
     * 批量根据笔记id查询其计数信息
     * @param noteIds
     * @return
     */
    List<NoteCountDO> selectByNoteIds(@Param("noteIds") List<Long> noteIds);

}