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
import org.springframework.data.crossstore.ChangeSetPersister;
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
        Long noteId = addNoteContentReqDTO.getNoteId();
        // 笔记内容
        String content = addNoteContentReqDTO.getContent();
        NoteContentDO noteContentDO = NoteContentDO.builder()
                .id(UUID.randomUUID())  // TODO: 暂时用 UUID, 目的是为了下一章讲解压测，不用动态传笔记 ID。后续改为笔记服务传过来的笔记 ID
                .content(content)
                .build();
        noteContentRepository.save(noteContentDO);
        return Response.success(noteContentDO);
    }

    @Override
    public Response<FindNoteContentRespDTO> findNoteContent(FindNoteContentReqDTO findNoteContentReqDTO) {
        String noteId = findNoteContentReqDTO.getNoteId();
        Optional<NoteContentDO> optional = noteContentRepository.findById(UUID.fromString(noteId));
        if (optional.isPresent()) {
            FindNoteContentRespDTO findNoteContentRespDTO = FindNoteContentRespDTO.builder()
                    .content(optional.get().getContent())
                    .noteId(optional.get().getId())
                    .build();
            return Response.success(findNoteContentRespDTO);
        } else {
            throw new BusinessException(ResponseCodeEnum.NOTE_CONTENT_NOT_FOUND);
        }
    }

    @Override
    public Response<?> deleteNoteContent(DeleteNoteContentReqDTO deleteNoteContentReqDTO) {
        String noteId = deleteNoteContentReqDTO.getNoteId();
        noteContentRepository.deleteById(UUID.fromString(noteId));
        return Response.success();
    }
}
