package com.danby.happynode.framework.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum StatusEnum {
    ENABLED(1), DISABLED(0);
    private final Integer value;
}
