package com.zhoushuo.eaqb.question.bank.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum QuestionProcessTaskStatusEnum {
    PENDING_DISPATCH("PENDING_DISPATCH"),
    DISPATCHED("DISPATCHED"),
    CALLBACK_RECEIVED("CALLBACK_RECEIVED"),
    SUCCEEDED("SUCCEEDED"),
    FAILED("FAILED");

    private final String code;
}
