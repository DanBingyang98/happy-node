package com.danby.happynode.search.service;

import com.danby.happynode.framework.common.response.PageResponse;
import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.search.dto.RebuildNoteDocumentReqDTO;
import com.danby.happynode.search.model.vo.SearchNoteReqVO;
import com.danby.happynode.search.model.vo.SearchNoteRespVO;

public interface NoteService {
    /**
     * 搜索笔记
     * @param searchNoteReqVO
     * @return
     */
    PageResponse<SearchNoteRespVO> searchNote(SearchNoteReqVO searchNoteReqVO);

    /**
     * 重建笔记文档
     * @param rebuildNoteDocumentReqDTO
     * @return
     */
    Response<Long> rebuildDocument(RebuildNoteDocumentReqDTO rebuildNoteDocumentReqDTO);
}
