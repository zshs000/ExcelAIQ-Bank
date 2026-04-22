package com.zhoushuo.eaqb.question.bank.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CallbackInboxStatusEnum {
    RECEIVED("RECEIVED"),
    PROCESSED("PROCESSED"),
    FAILED("FAILED");

    private final String code;
}
