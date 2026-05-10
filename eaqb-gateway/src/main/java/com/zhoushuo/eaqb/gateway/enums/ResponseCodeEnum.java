package com.zhoushuo.eaqb.gateway.enums;

import com.zhoushuo.framework.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResponseCodeEnum implements BaseExceptionInterface {

    // ----------- 通用异常状态码 -----------
    SYSTEM_ERROR("GW-10000", "系统繁忙，请稍后再试"),
    UNAUTHORIZED("GW-10001", "未登录或登录已过期"),
    FORBIDDEN("GW-10002", "权限不足"),


    // ----------- 业务异常状态码 -----------
    ;

    // 异常码
    private final String errorCode;
    // 错误信息
    private final String errorMessage;

}