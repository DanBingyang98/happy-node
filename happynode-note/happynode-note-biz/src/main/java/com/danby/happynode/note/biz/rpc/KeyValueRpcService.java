package com.danby.happynode.note.biz.rpc;

import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.kv.dto.api.KeyValueFeign;
import com.danby.happynode.kv.dto.req.AddNoteContentReqDTO;
import com.danby.happynode.kv.dto.req.DeleteNoteContentReqDTO;
import com.danby.happynode.kv.dto.req.FindNoteContentReqDTO;
import com.danby.happynode.kv.dto.resp.FindNoteContentRespDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class KeyValueRpcService {
    @Autowired
    private KeyValueFeign keyValueFeign;

    /**
     * 保存笔记内容
     *
     * @param uuid
     * @param content
     * @return
     */
    public boolean addNoteContent(String uuid, String content) {
        AddNoteContentReqDTO reqDTO = AddNoteContentReqDTO.builder()
                .uuid(uuid)
                .content(content)
                .build();
        Response<?> response = keyValueFeign.addNoteContent(reqDTO);
        if (Objects.isNull(response) || !response.isSuccess()) {
            return false;
        }
        return true;
    }

    /**
     * 删除笔记内容
     *
     * @param uuid
     * @return
     */
    public boolean deleteNoteContent(String uuid) {
        DeleteNoteContentReqDTO reqDTO = DeleteNoteContentReqDTO.builder()
                .uuid(uuid)
                .build();
        Response<?> response = keyValueFeign.deleteNoteContent(reqDTO);
        if (Objects.isNull(response) || !response.isSuccess()) {
            return false;
        }
        return true;
    }

    /**
     * 查询笔记内容
     *
     * @param uuid
     * @return
     */
    public String findNoteContent(String uuid) {
        FindNoteContentReqDTO findNoteContentReqDTO = FindNoteContentReqDTO.builder()
                .uuid(uuid)
                .build();
        Response<FindNoteContentRespDTO> response = keyValueFeign.findNoteContent(findNoteContentReqDTO);
        if (Objects.isNull(response) || !response.isSuccess() || Objects.isNull(response.getData())) {
            return null;
        }
        return response.getData().getContent();
    }
}
