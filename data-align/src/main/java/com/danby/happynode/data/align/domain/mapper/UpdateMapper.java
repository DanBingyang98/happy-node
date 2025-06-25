package com.danby.happynode.data.align.domain.mapper;

import org.apache.ibatis.annotations.Param;

public interface UpdateMapper {
    /**
     * 更新 t_user_count 计数表总关注数
     * @param userId
     * @return
     */
    int updateUserFollowingTotalByUserId(@Param("userId") long userId, @Param("followingTotal") int followingTotal);

    /**
     * 更新 t_note_count 计数表笔记点赞数
     */
    int updateNoteLikeTotalByUserId(@Param("noteId") long noteId, @Param("noteLikeTotal") int noteLikeTotal);

    /**
     * 更新 t_user_count 计数表总粉丝数
     * @param userId
     * @return
     */
    int updateUserFansTotalByUserId(@Param("userId") long userId, @Param("fansTotal") int fansTotal);

    /**
     * 更新 t_note_count 计数表笔记收藏数
     */
    int updateNoteCollectTotalByNoteId(@Param("noteId") long noteId, @Param("noteCollectTotal") int noteCollectTotal);

    /**
     * 批量更新 t_user_count 计数表总笔记数
     */
    int updateUserNoteTotalByUserId(@Param("userId") long userId, @Param("fansTotal") int fansTotal);

    /**
     * 更新 t_user_count 计数表用户收藏数
     * @param userId
     * @return
     */
    int updateUserCollectTotalByUserId(@Param("userId") long userId, @Param("collectTotal") int collectTotal);

    /**
     * 更新 t_user_count 计数表用户点赞数
     * @param userId
     * @return
     */
    int updateUserLikeTotalByUserId(@Param("userId") long userId, @Param("likeTotal") int likeTotal);

}
