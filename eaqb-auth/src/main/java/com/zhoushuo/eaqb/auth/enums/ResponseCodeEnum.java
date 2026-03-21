package com.zhoushuo.eaqb.auth.enums;

import com.zhoushuo.framework.commono.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResponseCodeEnum implements BaseExceptionInterface {



    // ----------- 通用异常状态码 -----------
    SYSTEM_ERROR("AUTH-10000", "出错啦，后台小哥正在努力修复中..."),
    PARAM_NOT_VALID("AUTH-10001", "参数错误"),

    // ----------- 业务异常状态码 -----------
    VERIFICATION_CODE_SEND_FREQUENTLY("AUTH-20000", "请求太频繁，请3分钟后再试"),

    VERIFICATION_CODE_ERROR("AUTH-20001", "验证码错误"),

    LOGIN_TYPE_ERROR("AUTH-20002", "登录类型错误"),
    USER_NOT_FOUND("AUTH-20003", "该用户不存在"),
    PHONE_OR_PASSWORD_ERROR("AUTH-20004", "手机号或密码错误"),
    LOGIN_FAIL("AUTH-20005", "登录失败"),
    VERIFICATION_CODE_DAILY_LIMIT_EXCEEDED("AUTH-20006", "该手机号今日验证码发送次数已达上限"),
    VERIFICATION_CODE_IP_DAILY_LIMIT_EXCEEDED("AUTH-20007", "该IP今日验证码发送次数已达上限"),
    VERIFICATION_CODE_PHONE_IN_BLACKLIST("AUTH-20008", "该手机号已被限制发送验证码"),
    PASSWORD_NOT_INITIALIZED("AUTH-20009", "该账号尚未设置密码，请先通过验证码登录后设置密码"),
    ;

    // ----------- 业务异常状态码 -----------



    // 异常码
    private final String errorCode;
    // 错误信息
    private final String errorMessage;

}
