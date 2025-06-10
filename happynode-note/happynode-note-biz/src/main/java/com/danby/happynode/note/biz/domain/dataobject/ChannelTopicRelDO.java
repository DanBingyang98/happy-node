package com.danby.happynode.note.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChannelTopicRelDO {
    private Long id;

    private Long channelId;

    private Long topicId;

    private Date createTime;

    private Date updateTime;
}