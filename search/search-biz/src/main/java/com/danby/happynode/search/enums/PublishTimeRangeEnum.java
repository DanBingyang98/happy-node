package com.danby.happynode.search.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

@Getter
@AllArgsConstructor
public enum PublishTimeRangeEnum {
    // 一天内
    ONE_DAY(0),
    // 一周内
    ONE_WEEK(1),
    // 一月内
    ONE_MONTH(2),
    // 半年内
    HALF_YEAR(3),
    // 一年内
    ONE_YEAR(4);

    private final Integer code;

    public static PublishTimeRangeEnum valueOf(Integer code) {
        for (PublishTimeRangeEnum value : PublishTimeRangeEnum.values()) {
            if (Objects.equals(code, value.getCode())) {
                return value;
            }
        }
        return null;
    }


}
