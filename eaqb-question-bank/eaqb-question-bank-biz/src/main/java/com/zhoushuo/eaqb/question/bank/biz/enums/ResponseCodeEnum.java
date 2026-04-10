package com.zhoushuo.eaqb.question.bank.biz.enums;

import com.zhoushuo.framework.commono.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResponseCodeEnum implements BaseExceptionInterface {

    SYSTEM_ERROR("QUESTION-10000", "出错啦，后台小哥正在努力修复中..."),
    PARAM_NOT_VALID("QUESTION-10001", "参数错误"),

    QUESTION_ID_GENERATE_FAILED("QUESTION-20000", "生成题目id失败"),
    QUESTION_CREATE_FAILED("QUESTION-20001", "创建题目失败，请稍后重试"),
    QUESTION_DELETE_FAILED("QUESTION-20002", "题目删除异常"),
    QUESTION_NOT_FOUND("QUESTION-20003", "题目不存在"),
    NO_PERMISSION("QUESTION-20004", "无权限操作此题目"),
    QUESTION_UPDATE_FAILED("QUESTION-20005", "更新题目失败"),
    QUESTION_SEND_FAILED("QUESTION-20006", "题目发送失败"),
    QUESTION_STATUS_NOT_ALLOWED("QUESTION-20007", "当前题目状态不允许执行该操作"),
    ID_GENERATE_FAILED("QUESTION-20008", "ID生成失败"),

    QUESTION_IMPORT_BATCH_CREATE_FAILED("QUESTION-20009", "创建导入批次失败"),
    QUESTION_IMPORT_BATCH_NOT_FOUND("QUESTION-20010", "导入批次不存在"),
    QUESTION_IMPORT_BATCH_STATUS_ILLEGAL("QUESTION-20011", "导入批次状态非法"),
    QUESTION_IMPORT_CHUNK_APPEND_FAILED("QUESTION-20012", "导入分块追加失败"),
    QUESTION_IMPORT_CHUNK_CONFLICT("QUESTION-20013", "导入分块幂等冲突"),
    QUESTION_IMPORT_BATCH_COUNT_MISMATCH("QUESTION-20014", "导入批次计数不一致"),
    QUESTION_IMPORT_COMMIT_FAILED("QUESTION-20015", "导入批次正式提交失败");

    private final String errorCode;
    private final String errorMessage;
}
