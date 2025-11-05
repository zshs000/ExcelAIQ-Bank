package com.zhoushuo.eaqb.oss.biz.enums;

import com.zhoushuo.framework.commono.exception.BaseExceptionInterface;
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
    MINIO_URL_GENERATE_ERROR("oss-20004", "oss短链接生成故障");





    // 异常码
    private final String errorCode;
    // 错误信息
    private final String errorMessage;

}