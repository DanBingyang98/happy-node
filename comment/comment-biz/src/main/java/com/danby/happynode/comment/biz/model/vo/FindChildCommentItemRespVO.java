package com.danby.happynode.comment.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FindChildCommentItemRespVO {

    /**
     * 评论 ID
     */
    private Long commentId;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 内容
     */
    private String content;

    /**
     * 图片
     */
    private String imageUrl;

    /**
     * 创建时间
     */
    private String createTime;

    /**
     * 点赞数
     */
    private Long likeTotal;

    /**
     * 回复用户名
     */
    private String replyUserName;

    /**
     * 所回复的用户ID
     */
    private Long replyUserId;
}
