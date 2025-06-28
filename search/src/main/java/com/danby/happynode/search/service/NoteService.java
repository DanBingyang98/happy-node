package com.danby.happynode.search.service;

import com.danby.happynode.framework.common.response.PageResponse;
import com.danby.happynode.search.model.vo.SearchNoteReqVO;
import com.danby.happynode.search.model.vo.SearchNoteRespVO;

public interface NoteService {
    /**
     * 搜索笔记
     * @param searchNoteReqVO
     * @return
     */
    PageResponse<SearchNoteRespVO> searchNote(SearchNoteReqVO searchNoteReqVO);
}
