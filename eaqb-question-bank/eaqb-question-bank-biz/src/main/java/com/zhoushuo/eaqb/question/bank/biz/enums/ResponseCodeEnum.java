package com.zhoushuo.eaqb.question.bank.biz.enums;

import com.zhoushuo.framework.commono.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResponseCodeEnum implements BaseExceptionInterface {

    // ----------- 通用异常状态码 -----------
    SYSTEM_ERROR("QUESTION-10000", "出错啦，后台小哥正在努力修复中..."),
    PARAM_NOT_VALID("QUESTION-10001", "参数错误"),



    QUESTION_ID_GENERATE_FAILED("QUESTION-20000", "生成题目id失败"),
    QUESTION_CREATE_FAILED("QUESTION-20001", "创建题目失败，请稍后重试");





    // 异常码
    private final String errorCode;
    // 错误信息
    private final String errorMessage;

}