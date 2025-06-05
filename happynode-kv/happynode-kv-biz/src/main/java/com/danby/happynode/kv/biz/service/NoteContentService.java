package com.danby.happynode.kv.biz.service;

import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.kv.dto.req.AddNoteContentReqDTO;
import com.danby.happynode.kv.dto.req.DeleteNoteContentReqDTO;
import com.danby.happynode.kv.dto.req.FindNoteContentReqDTO;
import com.danby.happynode.kv.dto.resp.FindNoteContentRespDTO;

public interface NoteContentService {
    /**
     * 添加笔记内容
     *
     * @param addNoteContentReqDTO
     * @return
     */
    Response<?> addNoteContent(AddNoteContentReqDTO addNoteContentReqDTO);

    /**
     * 查询笔记内容
     *
     * @param findNoteContentReqDTO
     * @return
     */
    Response<FindNoteContentRespDTO> findNoteContent(FindNoteContentReqDTO findNoteContentReqDTO);

    /***
     * 删除笔记内容
     *
     * @param deleteNoteContentReqDTO
     * @return
     */
    Response<?> deleteNoteContent(DeleteNoteContentReqDTO deleteNoteContentReqDTO);
}
