package com.danby.happynode.note.biz.service;

import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.note.biz.model.vo.PublishNoteReqVO;

public interface NoteService {
    Response<?> publishNote(PublishNoteReqVO publishNoteReqVO);
}
