package com.danby.happynode.count.api;

import com.danby.happynode.count.constants.ApiConstants;
import com.danby.happynode.count.dto.FindNoteCountsByIdRespDTO;
import com.danby.happynode.count.dto.FindNoteCountsByIdsReqDTO;
import com.danby.happynode.count.dto.FindUserCountsByIdReqDTO;
import com.danby.happynode.count.dto.FindUserCountsByIdRespDTO;
import com.danby.happynode.framework.common.response.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = ApiConstants.SERVICE_NAME, path = "/count")
public interface CountFeign {

    /**
     * 查询用户计数
     *
     * @param findUserCountsByIdReqDTO
     * @return
     */
    @PostMapping(value = "/user/data")
    Response<FindUserCountsByIdRespDTO> findUserCountData(@RequestBody FindUserCountsByIdReqDTO findUserCountsByIdReqDTO);

    /**
     * 批量查询笔记计数
     *
     * @param findNoteCountsByIdsReqDTO
     * @return
     */
    @PostMapping(value = "/note/data")
    Response<List<FindNoteCountsByIdRespDTO>> findNotesCountData(@RequestBody FindNoteCountsByIdsReqDTO findNoteCountsByIdsReqDTO);
}
