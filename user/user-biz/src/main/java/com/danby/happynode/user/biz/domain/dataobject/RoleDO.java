package com.danby.happynode.user.biz.domain.dataobject;

import lombok.*;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class RoleDO {
    private Long id;

    private String roleName;

    private String roleKey;

    private Byte status;

    private Integer sort;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Boolean isDeleted;
}