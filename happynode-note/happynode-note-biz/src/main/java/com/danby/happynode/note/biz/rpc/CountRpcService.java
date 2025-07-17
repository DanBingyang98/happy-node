package com.danby.happynode.note.biz.rpc;

import cn.hutool.core.collection.CollUtil;
import com.danby.happynode.count.api.CountFeign;
import com.danby.happynode.count.dto.FindNoteCountsByIdRespDTO;
import com.danby.happynode.count.dto.FindNoteCountsByIdsReqDTO;
import com.danby.happynode.framework.common.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class CountRpcService {

    @Autowired
    private CountFeign countFeign;

    /***
     * 批量查询笔记计数
     * @param noteIds
     * @return
     */
    public List<FindNoteCountsByIdRespDTO> findNoteCountsByNoteIds(List<Long> noteIds) {
        FindNoteCountsByIdsReqDTO findNoteCountsByIdsReqDTO = FindNoteCountsByIdsReqDTO.builder().noteIds(noteIds).build();
        Response<List<FindNoteCountsByIdRespDTO>> notesCountData = countFeign.findNotesCountData(findNoteCountsByIdsReqDTO);
        if (Objects.nonNull(notesCountData) && notesCountData.isSuccess() && CollUtil.isNotEmpty(notesCountData.getData())) {
            return notesCountData.getData();
        }
        return null;

    }
}
