package com.danby.happynode.note.biz.convert;

import com.danby.happynode.note.biz.domain.dataobject.NoteDO;
import com.danby.happynode.note.biz.model.dto.PublishNoteDTO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface NoteConvert {
    /**
     * 初始化 convert 实例
     */
    NoteConvert INSTANCE = Mappers.getMapper(NoteConvert.class);

    /***
     * 将 NoteDO 转换成 PublishNoteDTO
     * @param noteDO
     * @return
     */
    PublishNoteDTO convertDO2DTO(NoteDO noteDO);

    /***
     * 将 PublishNoteDTO 转换成 NoteDO
     * @param publishNoteDTO
     * @return
     */
    NoteDO convertDTO2DO(PublishNoteDTO publishNoteDTO);

}
