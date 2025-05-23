package com.danby.happynode.framework.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum DeleteEnum {
    YES(true), NO(false);
    private final Boolean value;
}
