package com.danby.happynode.count.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LikeUnlikeCommentTypeEnum {
    // 点赞
    LIKE(1),
    // 取消点赞
    UNLIKE(0),
    ;

    private final Integer code;

    public static LikeUnlikeCommentTypeEnum valueOf(Integer code) {
        for (LikeUnlikeCommentTypeEnum value : LikeUnlikeCommentTypeEnum.values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
