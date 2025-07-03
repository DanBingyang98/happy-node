package com.danby.happynode.comment.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.danby.framework.context.holder.LoginUserContextHolder;
import com.danby.happynode.comment.biz.constant.MQConstants;
import com.danby.happynode.comment.biz.domain.dataobject.CommentDO;
import com.danby.happynode.comment.biz.domain.mapper.CommentDOMapper;
import com.danby.happynode.comment.biz.domain.mapper.NoteCountDOMapper;
import com.danby.happynode.comment.biz.model.dto.PublishCommentMqDTO;
import com.danby.happynode.comment.biz.model.vo.FindCommentItemRespVO;
import com.danby.happynode.comment.biz.model.vo.FindCommentPageListReqVO;
import com.danby.happynode.comment.biz.model.vo.PublishCommentReqVO;
import com.danby.happynode.comment.biz.retry.SendMQRetryHelper;
import com.danby.happynode.comment.biz.rpc.DistributedIdGeneratorRpcService;
import com.danby.happynode.comment.biz.rpc.KeyValueRpcService;
import com.danby.happynode.comment.biz.rpc.UserRpcService;
import com.danby.happynode.comment.biz.service.CommentService;
import com.danby.happynode.framework.common.constant.DateConstants;
import com.danby.happynode.framework.common.response.PageResponse;
import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.framework.common.util.DateUtils;
import com.danby.happynode.kv.dto.req.FindCommentContentReqDTO;
import com.danby.happynode.kv.dto.resp.FindCommentContentRespDTO;
import com.danby.happynode.user.api.UserServiceFeign;
import com.danby.happynode.user.dto.resp.FindUserByIdRespDTO;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CommentServiceImpl implements CommentService {

    @Autowired
    private SendMQRetryHelper sendMQRetryHelper;
    @Autowired
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Autowired
    private NoteCountDOMapper noteCountDOMapper;
    @Autowired
    private CommentDOMapper commentDOMapper;
    @Autowired
    private KeyValueRpcService keyValueRpcService;
    @Autowired
    private UserServiceFeign userServiceFeign;
    @Autowired
    private UserRpcService userRpcService;

    @Override
    public Response<?> publishComment(PublishCommentReqVO publishCommentReqVO) {
        // 评论正文
        String content = publishCommentReqVO.getContent();
        // 附近图片
        String imageUrl = publishCommentReqVO.getImageUrl();
        // 调用远程rpc服务，生成分布式commentId
        String commentId = distributedIdGeneratorRpcService.getGeneratedCommentId();

        // 评论内容和图片不能同时为空
        Preconditions.checkArgument(StringUtils.isNotBlank(content) || StringUtils.isNotBlank(imageUrl),
                "评论正文和图片不能同时为空");
        Long commentCreatorId = LoginUserContextHolder.getUserId();
        // 发送MQ消息

        // 1. 构建消息体 DTO
        PublishCommentMqDTO publishCommentMqDTO = PublishCommentMqDTO.builder()
                .replyCommentId(publishCommentReqVO.getReplyCommentId())  // 回复的哪个评论（评论 ID）
                .noteId(publishCommentReqVO.getNoteId()) // 所评论的笔记 ID
                .content(content) // 评论的文本内容
                .imageUrl(imageUrl) // 评论的图片url
                .createTime(LocalDateTime.now()) // 创建时间
                .creatorId(commentCreatorId) // 发布评论者的id
                .commentId(Long.valueOf(commentId)) // 评论id
                .build();
        // 2. 通过SendMQRetryHelper发送消息
//        sendMQRetryHelper.send(MQConstants.TOPIC_PUBLISH_COMMENT, publishCommentMqDTO);

        sendMQRetryHelper.asyncSend(MQConstants.TOPIC_PUBLISH_COMMENT, publishCommentMqDTO);

        return Response.success();
    }

    /**
     * 评论列表分页查询
     *
     * @param findCommentPageListReqVO
     * @return
     */
    @Override
    public PageResponse<FindCommentItemRespVO> findCommentPageList(FindCommentPageListReqVO findCommentPageListReqVO) {
        // 笔记 ID
        Long noteId = findCommentPageListReqVO.getNoteId();
        // 当前页码
        Integer pageNo = findCommentPageListReqVO.getPageNo();
        // 每页展示一级评论数
        long pageSize = 10;

        // TODO: 先从缓存中查

        Long count = noteCountDOMapper.selectCommentTotalByNoteId(noteId);
        if (count == null || count == 0L) {
            return PageResponse.success(null, pageNo, pageSize);
        }
        // 分页返参
        List<FindCommentItemRespVO> commentRespVOS = null;
        if (count > 0) {
            commentRespVOS = Lists.newArrayList();
            // 先查一级评论
            long offset = PageResponse.getOffset(pageNo, pageSize);
            List<CommentDO> levelOneCommentDOS = commentDOMapper.selectPageList(noteId, offset, pageSize);
            List<Long> levelTwoCommentIds = levelOneCommentDOS.stream()
                    .map(CommentDO::getFirstReplyCommentId)
                    .filter(firstReplyCommentId -> firstReplyCommentId != 0)
                    .toList();
            // 查询二级评论
            Map<Long, CommentDO> commentIdAndDOMap = null;
            List<CommentDO> levelTwoCommentDOS = null;
            if (!levelTwoCommentIds.isEmpty()) {
                levelTwoCommentDOS = commentDOMapper.selectByCommentIds(levelTwoCommentIds);
                // 转 Map 集合，方便后续拼装数据
                commentIdAndDOMap = levelTwoCommentDOS.stream()
                        .collect(Collectors.toMap(CommentDO::getId, commentDO -> commentDO));
            }
            // 调用 KV 服务需要的入参
            List<FindCommentContentReqDTO> findCommentContentReqDTOs = new ArrayList<>();
            // 调用用户服务的入参
            List<Long> userIds = new ArrayList<>();
            // 将一级评论和二级评论合并到一起
            List<CommentDO> allCommentDOS = new ArrayList<>();
            allCommentDOS.addAll(levelOneCommentDOS);
            allCommentDOS.addAll(levelTwoCommentDOS);
            // 循环提取 RPC 调用需要的入参数据
            allCommentDOS.forEach(commentDO -> {
                // 构建调用 KV 服务批量查询评论内容的入参
                Boolean isContentEmpty = commentDO.getIsContentEmpty();
                if (!isContentEmpty) {
                    FindCommentContentReqDTO findCommentContentReqDTO = FindCommentContentReqDTO.builder()
                            .contentId(commentDO.getContentUuid())
                            .yearMonth(DateConstants.DATE_FORMAT_Y_M.format(commentDO.getCreateTime()))
                            .build();
                    findCommentContentReqDTOs.add(findCommentContentReqDTO);
                }
                // 构建调用用户服务批量查询用户信息的入参
                userIds.add(commentDO.getUserId());
            });
            // RPC: 调用 KV 服务，批量获取评论内容
            List<FindCommentContentRespDTO> findCommentContentRespDTOS = keyValueRpcService.batchFindCommentContent(noteId, findCommentContentReqDTOs);
            // DTO 集合转 Map, 方便后续拼装数据
            Map<String, String> commentUuidAndContentMap = null;
            if (CollUtil.isNotEmpty(findCommentContentRespDTOS)) {
                commentUuidAndContentMap = findCommentContentRespDTOS.stream().collect(Collectors.toMap(FindCommentContentRespDTO::getContentId, FindCommentContentRespDTO::getContent));
            }
            // RPC: 调用用户服务，批量获取用户信息
            List<FindUserByIdRespDTO> findUserByIdRespDTOS = userRpcService.findUserByIds(userIds);
            // DTO 集合转 Map, 方便后续拼装数据
            Map<Long, FindUserByIdRespDTO> userIdAndDTOMap = null;
            if (CollUtil.isNotEmpty(findUserByIdRespDTOS)) {
                userIdAndDTOMap = findUserByIdRespDTOS.stream().collect(Collectors.toMap(FindUserByIdRespDTO::getId, dto -> dto));
            }
            // DO 转 VO 组装数据
            for (CommentDO levelOneCommentDO : levelOneCommentDOS) {
                // 一级评论
                Long userId = levelOneCommentDO.getUserId();
                FindCommentItemRespVO oneLevelCommentRspVO = FindCommentItemRespVO.builder()
                        .userId(userId)
                        .commentId(levelOneCommentDO.getId())
                        .imageUrl(levelOneCommentDO.getImageUrl())
                        .createTime(DateUtils.formatRelativeTime(levelOneCommentDO.getCreateTime()))
                        .likeTotal(levelOneCommentDO.getLikeTotal())
                        .childCommentTotal(levelOneCommentDO.getChildCommentTotal())
                        .build();
                // 用户信息
                setUserInfo(commentIdAndDOMap, userIdAndDTOMap, userId, oneLevelCommentRspVO);
                // 笔记内容
                setCommentContent(commentUuidAndContentMap, levelOneCommentDO, oneLevelCommentRspVO);

                // 二级评论
                Long firstReplyCommentId = levelOneCommentDO.getFirstReplyCommentId();
                if (CollUtil.isNotEmpty(commentIdAndDOMap)) {
                    CommentDO firstReplyCommentDO = commentIdAndDOMap.get(firstReplyCommentId);
                    if (Objects.nonNull(firstReplyCommentDO)) {
                        Long firstReplyCommentUserId = firstReplyCommentDO.getUserId();
                        FindCommentItemRespVO firstReplyCommentRespVO = FindCommentItemRespVO.builder()
                                .userId(firstReplyCommentUserId)
                                .commentId(firstReplyCommentDO.getId())
                                .imageUrl(firstReplyCommentDO.getImageUrl())
                                .createTime(DateUtils.formatRelativeTime(firstReplyCommentDO.getCreateTime()))
                                .likeTotal(firstReplyCommentDO.getLikeTotal())
                                .childCommentTotal(firstReplyCommentDO.getChildCommentTotal())
                                .build();
                        // 用户信息
                        setUserInfo(commentIdAndDOMap, userIdAndDTOMap, firstReplyCommentUserId, firstReplyCommentRespVO);
                        // 笔记内容
                        setCommentContent(commentUuidAndContentMap, firstReplyCommentDO, firstReplyCommentRespVO);
                        // 二级评论VO添加到一级评论VO
                        oneLevelCommentRspVO.setFirstReplyComment(firstReplyCommentRespVO);
                    }
                }
                commentRespVOS.add(oneLevelCommentRspVO);
            }
        }
        return PageResponse.success(commentRespVOS, pageNo, count, pageSize);
    }

    /**
     * 设置评论内容
     *
     * @param commentUuidAndContentMap
     * @param commentDO
     * @param findCommentItemRespVO
     */
    private void setCommentContent(Map<String, String> commentUuidAndContentMap, CommentDO commentDO, FindCommentItemRespVO findCommentItemRespVO) {
        if (CollUtil.isNotEmpty(commentUuidAndContentMap)) {
            String contentUuid = commentDO.getContentUuid();
            if (StrUtil.isNotBlank(contentUuid)) {
                String content = commentUuidAndContentMap.get(contentUuid);
                if (StrUtil.isNotBlank(content)) {
                    findCommentItemRespVO.setContent(content);
                }
            }
        }
    }

    /**
     * 设置用户信息
     *
     * @param commentIdAndDOMap
     * @param userIdAndDTOMap
     * @param userId
     * @param findCommentItemRespVO
     */
    private void setUserInfo(Map<Long, CommentDO> commentIdAndDOMap, Map<Long, FindUserByIdRespDTO> userIdAndDTOMap, Long userId, FindCommentItemRespVO findCommentItemRespVO) {
        FindUserByIdRespDTO findUserByIdRespDTO = userIdAndDTOMap.get(userId);
        if (Objects.nonNull(findUserByIdRespDTO)) {
            findCommentItemRespVO.setNickname(findUserByIdRespDTO.getNickName());
            findCommentItemRespVO.setAvatar(findUserByIdRespDTO.getAvatar());
        }
    }
}
