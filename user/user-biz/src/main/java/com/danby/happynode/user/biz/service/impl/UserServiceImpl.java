package com.danby.happynode.user.biz.service.impl;

import com.danby.framework.context.holder.LoginUserContextHolder;
import com.danby.happynode.framework.common.exception.BusinessException;
import com.danby.happynode.framework.common.response.Response;
import com.danby.happynode.framework.common.util.ParamUtils;
import com.danby.happynode.user.biz.domain.dataobject.UserDO;
import com.danby.happynode.user.biz.domain.mapper.UserDOMapper;
import com.danby.happynode.user.biz.enums.ResponseCodeEnum;
import com.danby.happynode.user.biz.enums.SexEnum;
import com.danby.happynode.user.biz.model.vo.UpdateUserInfoReqVO;
import com.danby.happynode.user.biz.rpc.OssRpcService;
import com.danby.happynode.user.biz.service.UserService;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserDOMapper userDOMapper;

    @Autowired
    private OssRpcService ossRpcService;

    /**
     * 更新用户信息
     *
     * @param updateUserInfoReqVO
     * @return
     */
    @Override
    public Response<?> updateUserInfo(UpdateUserInfoReqVO updateUserInfoReqVO) {
        UserDO userDO = new UserDO();
        // 设置当前需要更新的用户 ID
        userDO.setId(LoginUserContextHolder.getUserId());
        // 标识位：是否需要更新
        boolean needUpdate = false;
        // 头像
        MultipartFile avatarFile = updateUserInfoReqVO.getAvatar();

        if (Objects.nonNull(avatarFile)) {
            String avatar = ossRpcService.uploadFile(avatarFile);
            log.info("==> 调用 oss 服务成功，上传头像，url：{}", avatar);
            if (StringUtils.isBlank(avatar)) {
                throw new BusinessException(ResponseCodeEnum.UPLOAD_AVATAR_FAIL);
            } else {
                userDO.setAvatar(avatar);
                needUpdate = true;
            }
        }

        // 昵称
        String nickname = updateUserInfoReqVO.getNickname();
        if (StringUtils.isNotBlank(nickname)) {
            Preconditions.checkArgument(ParamUtils.checkNickname(nickname), ResponseCodeEnum.NICK_NAME_VALID_FAIL.getMessage());
            userDO.setNickname(nickname);
            needUpdate = true;
        }

        // 小哈书号
        String happynodeId = updateUserInfoReqVO.getHappynodeId();
        if (StringUtils.isNotBlank(happynodeId)) {
            Preconditions.checkArgument(ParamUtils.checkHappynodeId(happynodeId), ResponseCodeEnum.HAPPYNODE_ID_VALID_FAIL.getMessage());
            userDO.setHappynodeId(happynodeId);
            needUpdate = true;
        }

        // 性别
        Integer sex = updateUserInfoReqVO.getSex();
        if (Objects.nonNull(sex)) {
            Preconditions.checkArgument(SexEnum.isValid(sex), ResponseCodeEnum.SEX_VALID_FAIL.getMessage());
            userDO.setSex(sex);
            needUpdate = true;
        }

        // 生日
        LocalDate birthday = updateUserInfoReqVO.getBirthday();
        if (Objects.nonNull(birthday)) {
            userDO.setBirthday(birthday);
            needUpdate = true;
        }

        // 个人简介
        String introduction = updateUserInfoReqVO.getIntroduction();
        if (StringUtils.isNotBlank(introduction)) {
            Preconditions.checkArgument(ParamUtils.checkLength(introduction, 100), ResponseCodeEnum.INTRODUCTION_VALID_FAIL.getMessage());
            userDO.setIntroduction(introduction);
            needUpdate = true;
        }

        // 背景图
        MultipartFile backgroundImgFile = updateUserInfoReqVO.getBackgroundImg();
        if (Objects.nonNull(backgroundImgFile)) {
            String background = ossRpcService.uploadFile(backgroundImgFile);
            log.info("==> 调用 oss 服务成功，上传背景图，url：{}", background);
            if (StringUtils.isBlank(background)) {
                throw new BusinessException(ResponseCodeEnum.UPLOAD_BACKGROUND_IMG_FAIL);
            } else {
                userDO.setBackgroundImg(background);
                needUpdate = true;
            }
        }

        if (needUpdate) {
            // 更新用户信息
            userDO.setUpdateTime(LocalDateTime.now());
            userDOMapper.updateByPrimaryKeySelective(userDO);
        }
        return Response.success();
    }
}
