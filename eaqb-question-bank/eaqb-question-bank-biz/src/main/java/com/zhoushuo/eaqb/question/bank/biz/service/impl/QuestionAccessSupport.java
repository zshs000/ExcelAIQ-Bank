package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.enums.ResponseCodeEnum;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.exception.BizException;
import org.springframework.stereotype.Component;

@Component
/**
 * 题库访问辅助组件。
 * 统一封装“从登录上下文提取当前用户ID”的逻辑，避免业务类重复写同样的判空/判格式代码。
 */
public class QuestionAccessSupport {

    /**
     * 获取当前登录用户ID（必填）。
     * - 上下文中的 userId 不是数字：抛参数错误；
     * - 上下文中缺少 userId：抛参数错误。
     */
    public Long requireCurrentUserId() {
        Long userId;
        try {
            userId = LoginUserContextHolder.getUserId();
        } catch (NumberFormatException e) {
            throw bizException(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), "请求头 userId 必须是数字");
        }
        if (userId == null) {
            throw bizException(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), "请求头 userId 不能为空");
        }
        return userId;
    }

    /**
     * 构造带自定义 errorCode/errorMessage 的业务异常。
     * 用于把访问层校验失败统一映射为可感知的业务错误码。
     */
    private BizException bizException(String errorCode, String errorMessage) {
        return new BizException(errorCode, errorMessage);
    }
}
