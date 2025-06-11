package com.danby.happynode.note.biz.service;

import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.note.biz.model.vo.FindNoteDetailReqVO;
import com.danby.happynode.note.biz.model.vo.FindNoteDetailRespVO;
import com.danby.happynode.note.biz.model.vo.PublishNoteReqVO;
import com.danby.happynode.note.biz.model.vo.UpdateNoteReqVO;

public interface NoteService {
    /**
     * 发布笔记
     * @param publishNoteReqVO
     * @return
     */
    Response<?> publishNote(PublishNoteReqVO publishNoteReqVO);

    /**
     * 笔记详情
     * @param findNoteDetailReqVO
     * @return
     */
    Response<FindNoteDetailRespVO> findNoteDetail(FindNoteDetailReqVO findNoteDetailReqVO);

    /**
     * 笔记更新
     * @param updateNoteReqVO
     * @return
     */
    Response<?> updateNote(UpdateNoteReqVO updateNoteReqVO);
}
