package com.danby.happynode.kv.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class AddNoteContentReqDTO {
    @NotNull(message = "笔记 ID 不能为空")
    private Long noteId;
    @NotBlank(message = "笔记内容不能为空")
    private String content;
}
