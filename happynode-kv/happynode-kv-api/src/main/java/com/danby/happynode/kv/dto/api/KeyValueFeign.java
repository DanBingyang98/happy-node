package com.danby.happynode.kv.dto.api;

import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.kv.dto.constant.ApiConstants;
import com.danby.happynode.kv.dto.req.AddNoteContentReqDTO;
import com.danby.happynode.kv.dto.req.DeleteNoteContentReqDTO;
import com.danby.happynode.kv.dto.req.FindNoteContentReqDTO;
import com.danby.happynode.kv.dto.resp.FindNoteContentRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = ApiConstants.SERVICE_NAME, path = ApiConstants.SERVICE_PATH)
public interface KeyValueFeign {

    @PostMapping("/note/content/add")
    Response<?> addNoteContent(@RequestBody AddNoteContentReqDTO reqDTO);

    @PostMapping("/note/content/find")
    Response<FindNoteContentRespDTO> findNoteContent(@RequestBody FindNoteContentReqDTO reqDTO);

    @PostMapping("/note/content/delete")
    Response<?> deleteNoteContent(@RequestBody DeleteNoteContentReqDTO reqDTO);
}
