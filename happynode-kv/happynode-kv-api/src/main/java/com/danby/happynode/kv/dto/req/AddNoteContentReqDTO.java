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
    @NotNull(message = "笔记 UUID 不能为空")
    private String uuid;
    @NotBlank(message = "笔记内容不能为空")
    private String content;
}
