package com.zhoushuo.eaqb.question.bank.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum QuestionImportBatchStatusEnum {
    APPENDING("APPENDING"),
    READY("READY"),
    COMMITTED("COMMITTED"),
    FAILED("FAILED"),
    ABORTED("ABORTED");

    private final String code;
}
