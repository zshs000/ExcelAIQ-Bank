package com.zhoushuo.eaqb.user.biz.enums;

import com.zhoushuo.framework.commono.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResponseCodeEnum implements BaseExceptionInterface {

    // ----------- 通用异常状态码 -----------
    SYSTEM_ERROR("USER-10000", "出错啦，后台小哥正在努力修复中..."),
    PARAM_NOT_VALID("USER-10001", "参数错误"),

    // ----------- 业务异常状态码 -----------


    NICK_NAME_VALID_FAIL("USER-20001", "昵称请设置2-24个字符，不能使用@《/等特殊字符"),
    EAQB_ID_VALID_FAIL("USER-20002", "题库系统号请设置6-15个字符，仅可使用英文（必须）、数字、下划线"),
    SEX_VALID_FAIL("USER-20003", "性别错误"),
    INTRODUCTION_VALID_FAIL("USER-20004", "个人简介请设置1-100个字符"),
    FILE_SIZE_EXCEED("USER-20005", "文件大小超出限制"),
    UPLOAD_AVATAR_FAIL("USER-20006", "头像上传失败"),
    UPLOAD_BACKGROUND_IMG_FAIL("USER-20007", "背景图上传失败"),
    USER_NOT_FOUND("USER-20008", "该用户不存在"),
    ;

    // 异常码
    private final String errorCode;
    // 错误信息
    private final String errorMessage;
}
