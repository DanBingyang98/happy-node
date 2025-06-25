package com.danby.happynode.data.align.domain.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SelectMapper {

    /**
     * 日增量表：关注数计数变更 - 批量查询
     *
     * @param tableNameSuffix
     * @param batchSize
     * @return
     */
    List<Long> selectBatchFromDataAlignFollowingCountTempTable(@Param("tableNameSuffix") String tableNameSuffix,
                                                               @Param("batchSize") int batchSize);

    /**
     * 查询 t_following 关注表，获取关注总数
     *
     * @param userId
     * @return
     */
    int selectCountFromFollowingTableByUserId(long userId);

    /**
     * 日增量表：笔记点赞数变更 - 批量查询
     * @param tableNameSuffix
     * @param batchSize
     * @return
     */
    List<Long> selectBatchFromDataAlignNoteLikeCountTempTable(@Param("tableNameSuffix") String tableNameSuffix,
                                                              @Param("batchSize") int batchSize);

    /**
     * 查询 t_note_like 笔记点赞表，获取点赞总数
     * @param noteId
     * @return
     */
    int selectCountFromNoteLikeTableByNodeId(long noteId);

    /**
     * 日增量表：粉丝数计数变更 - 批量查询
     *
     * @param tableNameSuffix
     * @param batchSize
     * @return
     */
    List<Long> selectBatchFromDataAlignFansCountTempTable(@Param("tableNameSuffix") String tableNameSuffix,
                                                          @Param("batchSize") int batchSize);

    /**
     * 查询 t_fans 关注表，获取关注总数
     *
     * @param userId
     * @return
     */
    int selectCountFromFansTableByUserId(long userId);

    /**
     * 日增量表：笔记收藏数变更 - 批量查询
     * @param tableNameSuffix
     * @param batchSize
     * @return
     */
    List<Long> selectBatchFromDataAlignNoteCollectCountTempTable(@Param("tableNameSuffix") String tableNameSuffix,
                                                          @Param("batchSize") int batchSize);

    /**
     * 查询 t_note_collect 笔记收藏表，获取收藏总数
     * @param noteId
     * @return
     */
    int selectCountFromNoteCollectTableByNoteId(long noteId);


    /**
     * 日增量表：笔记发布 - 批量查询
     * @param tableNameSuffix
     * @param batchSize
     * @return
     */
    List<Long> selectBatchFromDataAlignNotePublishCountTempTable(@Param("tableNameSuffix") String tableNameSuffix,
                                                                 @Param("batchSize") int batchSize);

    /**
     * 查询 t_note 笔记表，获取用户笔记总数
     * @param creatorId
     * @return
     */
    int selectCountFromNoteTableByUserId(long creatorId);

    /**
     * 日增量表：关注数计数变更 - 批量查询
     *
     * @param tableNameSuffix
     * @param batchSize
     * @return
     */
    List<Long> selectBatchFromDataAlignUserCollectCountTempTable(@Param("tableNameSuffix") String tableNameSuffix,
                                                               @Param("batchSize") int batchSize);

    /**
     * 查询 t_note_collect 笔记收藏表，获取用户收藏总数
     * @param userId
     * @return
     */
    int selectCountFromNoteCollectTableByUserId(long userId);


    /**
     * 日增量表：关注数计数变更 - 批量查询
     *
     * @param tableNameSuffix
     * @param batchSize
     * @return
     */
    List<Long> selectBatchFromDataAlignUserLikeCountTempTable(@Param("tableNameSuffix") String tableNameSuffix,
                                                              @Param("batchSize") int batchSize);

    /**
     * 查询 t_note_like 笔记点赞表，获取用户点赞总数
     * @param userId
     * @return
     */
    int selectCountFromNoteLikeTableByUserId(long userId);

}
