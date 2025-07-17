package com.danby.happynode.note.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindPublishedNoteListRespVO {

    /**
     * 笔记分页数据
     */
    private List<NoteItemRespVO> noteItemRespVOList;

    /**
     * 下一页的游标
     */
    private Long nextCursor;
}
