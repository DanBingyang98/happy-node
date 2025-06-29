package com.danby.happynode.framework.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum StatusEnum {
    ENABLED(0), DISABLED(1);
    private final Integer value;
}
