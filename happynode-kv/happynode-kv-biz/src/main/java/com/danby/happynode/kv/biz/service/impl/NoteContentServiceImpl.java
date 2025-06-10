package com.danby.happynode.kv.biz.service.impl;

import com.danby.happynode.framework.common.exception.BusinessException;
import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.kv.biz.domain.dataobject.NoteContentDO;
import com.danby.happynode.kv.biz.domain.repository.NoteContentRepository;
import com.danby.happynode.kv.biz.enums.ResponseCodeEnum;
import com.danby.happynode.kv.biz.service.NoteContentService;
import com.danby.happynode.kv.dto.req.AddNoteContentReqDTO;
import com.danby.happynode.kv.dto.req.DeleteNoteContentReqDTO;
import com.danby.happynode.kv.dto.req.FindNoteContentReqDTO;
import com.danby.happynode.kv.dto.resp.FindNoteContentRespDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class NoteContentServiceImpl implements NoteContentService {

    @Autowired
    private NoteContentRepository noteContentRepository;

    @Override
    public Response<?> addNoteContent(AddNoteContentReqDTO addNoteContentReqDTO) {
        // 笔记 ID
        String uuid = addNoteContentReqDTO.getUuid();
        // 笔记内容
        String content = addNoteContentReqDTO.getContent();
        NoteContentDO noteContentDO = NoteContentDO.builder()
                .id(UUID.fromString(uuid))  // TODO: 暂时用 UUID, 目的是为了下一章讲解压测，不用动态传笔记 ID。后续改为笔记服务传过来的笔记 ID
                .content(content)
                .build();
        noteContentRepository.save(noteContentDO);
        return Response.success();
    }

    @Override
    public Response<FindNoteContentRespDTO> findNoteContent(FindNoteContentReqDTO findNoteContentReqDTO) {
        String uuid = findNoteContentReqDTO.getUuid();
        Optional<NoteContentDO> optional = noteContentRepository.findById(UUID.fromString(uuid));
        if (optional.isPresent()) {
            FindNoteContentRespDTO findNoteContentRespDTO = FindNoteContentRespDTO.builder()
                    .content(optional.get().getContent())
                    .uuid(optional.get().getId())
                    .build();
            return Response.success(findNoteContentRespDTO);
        } else {
            throw new BusinessException(ResponseCodeEnum.NOTE_CONTENT_NOT_FOUND);
        }
    }

    @Override
    public Response<?> deleteNoteContent(DeleteNoteContentReqDTO deleteNoteContentReqDTO) {
        String uuid = deleteNoteContentReqDTO.getUuid();
        noteContentRepository.deleteById(UUID.fromString(uuid));
        return Response.success();
    }
}
