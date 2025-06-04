package com.danby.happynode.user.biz.domain.dataobject;

import lombok.*;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class UserRoleDO {
    private Long id;

    private Long userId;

    private Long roleId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Boolean isDeleted;
}