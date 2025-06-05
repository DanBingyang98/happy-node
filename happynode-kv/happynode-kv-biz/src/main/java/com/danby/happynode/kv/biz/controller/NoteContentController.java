package com.danby.happynode.kv.biz.controller;

import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.kv.biz.service.NoteContentService;
import com.danby.happynode.kv.dto.req.AddNoteContentReqDTO;
import com.danby.happynode.kv.dto.req.DeleteNoteContentReqDTO;
import com.danby.happynode.kv.dto.req.FindNoteContentReqDTO;
import com.danby.happynode.kv.dto.resp.FindNoteContentRespDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/kv")
public class NoteContentController {

    @Autowired
    private NoteContentService noteContentService;

    @PostMapping("/note/content/add")
    public Response<?> addNoteContent(@Validated @RequestBody AddNoteContentReqDTO reqDTO) {
        return noteContentService.addNoteContent(reqDTO);
    }

    @PostMapping("/note/content/find")
    public Response<FindNoteContentRespDTO> findNoteContent(@Validated @RequestBody FindNoteContentReqDTO reqDTO) {
        return noteContentService.findNoteContent(reqDTO);
    }

    @PostMapping("/note/content/delete")
    public Response<?> deleteNoteContent(@Validated @RequestBody DeleteNoteContentReqDTO reqDTO) {
        return noteContentService.deleteNoteContent(reqDTO);
    }
}
