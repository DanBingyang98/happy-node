package com.danby.happynode.count.biz.service;

import com.danby.happynode.count.dto.FindNoteCountsByIdRespDTO;
import com.danby.happynode.count.dto.FindNoteCountsByIdsReqDTO;
import com.danby.happynode.framework.common.response.Response;

import java.util.List;

public interface NoteCountService {

    /***
     * 批量查询笔记计数
     * @param findNoteCountsByIdsReqDTO
     * @return
     */
    Response<List<FindNoteCountsByIdRespDTO>> findNoteCountsByIds(FindNoteCountsByIdsReqDTO findNoteCountsByIdsReqDTO);
}
