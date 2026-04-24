package com.zhoushuo.eaqb.question.bank.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OutboxEventStatusEnum {
    NEW("NEW"),
    SENT("SENT"),
    RETRYABLE("RETRYABLE"),
    FAILED("FAILED");

    private final String code;
}
