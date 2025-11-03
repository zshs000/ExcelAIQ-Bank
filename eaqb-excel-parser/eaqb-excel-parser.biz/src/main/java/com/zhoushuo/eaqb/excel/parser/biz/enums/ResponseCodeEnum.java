package com.zhoushuo.eaqb.excel.parser.biz.enums;

import com.zhoushuo.framework.commono.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResponseCodeEnum implements BaseExceptionInterface {

    // ----------- 通用异常状态码 -----------
    SYSTEM_ERROR("EXCEL-10000", "出错啦，后台小哥正在努力修复中..."),
    PARAM_NOT_VALID("EXCEl-10001", "参数错误"),

    // ----------- 业务异常状态码 -----------


    FILE_SIZE_EXCEED("EXCEL-20001", "文件大小超出限制"),
    INVALID_FILE_FORMAT("EXCEL-20002","文件名为空" ),
    FILE_TYPE_ERROR("EXCEL-20003", "文件格式不支持，请使用提供的模版"),
    FILE_READ_ERROR("EXCEL-20004","文件读取错误" ),
    FILE_UPLOAD_ERROR("EXCEL-20005", "文件上传错误"),
    NO_PERMISSION("EXCEL-20006","无权限查看" ),
    RECORD_NOT_FOUND("EXCEL-20007", "未查到相关记录"),
    FILE_EMPTY_ERROR("EXCEL-20008","文件内容为空" );

    // 异常码
    private final String errorCode;
    // 错误信息
    private final String errorMessage;
}
