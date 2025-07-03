package com.danby.happynode.kv.dto.api;

import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.kv.dto.constant.ApiConstants;
import com.danby.happynode.kv.dto.req.*;
import com.danby.happynode.kv.dto.resp.FindCommentContentRespDTO;
import com.danby.happynode.kv.dto.resp.FindNoteContentRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = ApiConstants.SERVICE_NAME, path = ApiConstants.SERVICE_PATH)
public interface KeyValueFeign {

    @PostMapping("/note/content/add")
    Response<?> addNoteContent(@RequestBody AddNoteContentReqDTO reqDTO);

    @PostMapping("/note/content/find")
    Response<FindNoteContentRespDTO> findNoteContent(@RequestBody FindNoteContentReqDTO reqDTO);

    @PostMapping("/note/content/delete")
    Response<?> deleteNoteContent(@RequestBody DeleteNoteContentReqDTO reqDTO);

    @PostMapping("/comment/content/batchAdd")
    Response<?> batchAddCommentContent(@RequestBody BatchAddCommentContentReqDTO batchAddCommentContentReqDTO);

    @PostMapping("/comment/content/batchFind")
    Response<List<FindCommentContentRespDTO>> batchFindCommentContent(@RequestBody BatchFindCommentContentReqDTO batchFindCommentContentReqDTO);
}
