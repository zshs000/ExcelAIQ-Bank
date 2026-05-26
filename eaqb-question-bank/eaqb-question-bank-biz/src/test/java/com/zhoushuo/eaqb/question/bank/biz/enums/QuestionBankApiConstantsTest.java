package com.zhoushuo.eaqb.question.bank.biz.enums;

import com.zhoushuo.eaqb.question.bank.constant.ApiConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestionBankApiConstantsTest {

    @Test
    void importBatchStatusIllegalCode_shouldMatchPublicApiConstant() {
        assertEquals(ResponseCodeEnum.QUESTION_IMPORT_BATCH_STATUS_ILLEGAL.getErrorCode(),
                ApiConstants.QUESTION_IMPORT_BATCH_STATUS_ILLEGAL);
    }
}
