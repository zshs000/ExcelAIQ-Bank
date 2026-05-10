package com.zhoushuo.eaqb.oss.biz.enums;

import com.zhoushuo.framework.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResponseCodeEnum implements BaseExceptionInterface {

    // ----------- 通用异常状态码 -----------
    SYSTEM_ERROR("OSS-10000", "出错啦，后台小哥正在努力修复中..."),
    PARAM_NOT_VALID("OSS-10001", "参数错误"),




    FILE_TYPE_ERROR("OSS-20002", "文件类型错误"),
    FILE_EMPTY_ERROR("OSS-20003", "文件不能为空"),
    FILE_ACCESS_URL_GENERATE_ERROR("OSS-20004", "文件访问链接生成失败");





    // 异常码
    private final String errorCode;
    // 错误信息
    private final String errorMessage;

}
