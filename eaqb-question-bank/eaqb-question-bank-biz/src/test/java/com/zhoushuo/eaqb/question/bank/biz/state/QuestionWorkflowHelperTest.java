package com.zhoushuo.eaqb.question.bank.biz.state;

import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionProcessStatusEnum;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionStatusActionEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuestionWorkflowHelperTest {

    @Test
    void normalizeMode_shouldUppercaseAndTrim() {
        assertEquals("GENERATE", QuestionWorkflowHelper.normalizeMode(" generate "));
        assertEquals("VALIDATE", QuestionWorkflowHelper.normalizeMode("validate"));
    }

    @Test
    void normalizeMode_invalidMode_shouldReturnNull() {
        assertNull(QuestionWorkflowHelper.normalizeMode("abc"));
    }

    @Test
    void normalizeModeOrFallback_blankMode_shouldReturnFallback() {
        assertEquals("GENERATE", QuestionWorkflowHelper.normalizeModeOrFallback(null, "GENERATE"));
        assertEquals("VALIDATE", QuestionWorkflowHelper.normalizeModeOrFallback(" ", "VALIDATE"));
    }

    @Test
    void resolvedStatusCode_shouldNormalizeStatusValue() {
        assertEquals(QuestionProcessStatusEnum.WAITING.getCode(),
                QuestionWorkflowHelper.resolvedStatusCode(" waiting "));
        assertEquals(QuestionProcessStatusEnum.WAITING.getCode(),
                QuestionWorkflowHelper.resolvedStatusCode(null));
    }

    @Test
    void nextStatusCodeOrNull_shouldResolveNextStatusFromStateMachine() {
        assertEquals(QuestionProcessStatusEnum.DISPATCHING.getCode(),
                QuestionWorkflowHelper.nextStatusCodeOrNull("WAITING", QuestionStatusActionEnum.SEND));
        assertEquals(QuestionProcessStatusEnum.PROCESSING.getCode(),
                QuestionWorkflowHelper.nextStatusCodeOrNull("DISPATCHING", QuestionStatusActionEnum.SEND_SUCCESS));
    }

    @Test
    void nextStatusCodeOrNull_invalidTransition_shouldReturnNull() {
        assertNull(QuestionWorkflowHelper.nextStatusCodeOrNull("COMPLETED", QuestionStatusActionEnum.AI_SUCCESS));
    }
}
