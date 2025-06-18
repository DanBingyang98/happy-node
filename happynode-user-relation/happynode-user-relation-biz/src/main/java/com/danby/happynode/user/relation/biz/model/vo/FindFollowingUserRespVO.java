package com.danby.happynode.user.relation.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindFollowingUserRespVO {
    private Long userId;

    private String avatar;

    private String nickname;

    private String introduction;
}
