package com.danby.happynode.data.align.rpc;

import com.danby.happynode.search.api.SearchFeign;
import com.danby.happynode.search.dto.RebuildNoteDocumentReqDTO;
import com.danby.happynode.search.dto.RebuildUserDocumentReqDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component

public class SearchRpcService {

    @Autowired
    private SearchFeign searchFeign;

    /**
     * 调用重建笔记文档接口
     * @param noteId
     */
    public void rebuildNoteDocument(Long noteId) {
        RebuildNoteDocumentReqDTO rebuildNoteDocumentReqDTO = RebuildNoteDocumentReqDTO.builder()
                .id(noteId)
                .build();

        searchFeign.rebuildNoteDocument(rebuildNoteDocumentReqDTO);
    }

    /**
     * 调用重建用户文档接口
     * @param userId
     */
    public void rebuildUserDocument(Long userId) {
        RebuildUserDocumentReqDTO rebuildUserDocumentReqDTO = RebuildUserDocumentReqDTO.builder()
                .id(userId)
                .build();

        searchFeign.rebuildUserDocument(rebuildUserDocumentReqDTO);
    }
}
