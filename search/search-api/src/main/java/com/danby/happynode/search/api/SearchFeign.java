package com.danby.happynode.search.api;

import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.search.constant.ApiConstants;
import com.danby.happynode.search.dto.RebuildNoteDocumentReqDTO;
import com.danby.happynode.search.dto.RebuildUserDocumentReqDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = ApiConstants.SERVICE_NAME, path = "search")
public interface SearchFeign {
    /**
     * 重建笔记文档
     *
     * @param rebuildNoteDocumentReqDTO
     * @return
     */
    @PostMapping(value = "/note/document/rebuild")
    Response<?> rebuildNoteDocument(@RequestBody RebuildNoteDocumentReqDTO rebuildNoteDocumentReqDTO);


    /**
     * 重建用户文档
     *
     * @param rebuildUserDocumentReqDTO
     * @return
     */
    @PostMapping(value = "/user/document/rebuild")
    Response<?> rebuildUserDocument(@RequestBody RebuildUserDocumentReqDTO rebuildUserDocumentReqDTO);

}
