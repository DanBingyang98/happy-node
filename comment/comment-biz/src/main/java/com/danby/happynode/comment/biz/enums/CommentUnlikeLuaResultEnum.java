package com.danby.happynode.comment.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CommentUnlikeLuaResultEnum {
    // 布隆过滤器不存在
    NOT_EXIST(-1L),
    // 评论已点赞
    COMMENT_LIKED(1L),
    // 评论未点赞
    COMMENT_NOT_LIKED(0L),
    ;

    private final Long value;

    public static CommentUnlikeLuaResultEnum valueOf(Long value) {
        for (CommentUnlikeLuaResultEnum commentUnlikeLuaResultEnum : values()) {
            if (commentUnlikeLuaResultEnum.getValue().equals(value)) {
                return commentUnlikeLuaResultEnum;
            }
        }
        return null;
    }
}
