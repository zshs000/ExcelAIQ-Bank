package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionCallbackInboxDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionProcessTaskDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionValidationRecordDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionCallbackInboxDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionProcessTaskDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.domain.mapper.QuestionValidationRecordDOMapper;
import com.zhoushuo.eaqb.question.bank.biz.model.AIProcessResultMessage;
import com.zhoushuo.eaqb.question.bank.biz.rpc.DistributedIdGeneratorRpcService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionCallbackAppServiceTest {

    @Mock
    private QuestionDOMapper questionDOMapper;

    @Mock
    private QuestionValidationRecordDOMapper questionValidationRecordDOMapper;

    @Mock
    private QuestionProcessTaskDOMapper questionProcessTaskDOMapper;

    @Mock
    private QuestionCallbackInboxDOMapper questionCallbackInboxDOMapper;

    @Mock
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;

    @InjectMocks
    private QuestionCallbackAppService questionCallbackAppService;

    @Test
    void batchUpdateSuccessQuestions_generateMode_shouldOverwriteAnswer() {
        AIProcessResultMessage message = new AIProcessResultMessage("501", "GENERATE", 1,
                "AI生成答案", "NA", null);
        message.setTaskId("9501");
        message.setCallbackKey("cb-9501");
        when(questionCallbackInboxDOMapper.selectByCallbackKey("cb-9501")).thenReturn(null);
        when(distributedIdGeneratorRpcService.nextQuestionBankEntityId()).thenReturn("39001");
        when(questionCallbackInboxDOMapper.insert(any(QuestionCallbackInboxDO.class))).thenReturn(1);
        when(questionDOMapper.selectByPrimaryKey(501L))
                .thenReturn(QuestionDO.builder().id(501L).processStatus("PROCESSING").answer(null).build());
        when(questionProcessTaskDOMapper.selectByPrimaryKey(9501L))
                .thenReturn(QuestionProcessTaskDO.builder().id(9501L).questionId(501L).mode("GENERATE")
                        .attemptNo(1).taskStatus("DISPATCHED").build());
        when(questionProcessTaskDOMapper.selectActiveTaskByQuestionId(501L))
                .thenReturn(QuestionProcessTaskDO.builder().id(9501L).questionId(501L).mode("GENERATE")
                        .attemptNo(1).taskStatus("DISPATCHED").build());
        when(questionDOMapper.transitStatusAndAnswerAndReviewMode(501L, "PROCESSING", "REVIEW_PENDING",
                "AI生成答案", "GENERATE")).thenReturn(1);
        when(questionProcessTaskDOMapper.updateTaskStatus(9501L, "DISPATCHED", "SUCCEEDED", null)).thenReturn(1);
        when(questionCallbackInboxDOMapper.updateConsumeStatus("cb-9501", "RECEIVED", "PROCESSED")).thenReturn(1);

        int count = questionCallbackAppService.batchUpdateSuccessQuestions(Map.of("501", message));

        assertEquals(1, count);
        verify(questionDOMapper).transitStatusAndAnswerAndReviewMode(501L, "PROCESSING", "REVIEW_PENDING",
                "AI生成答案", "GENERATE");
        verify(questionProcessTaskDOMapper).updateTaskStatus(9501L, "DISPATCHED", "SUCCEEDED", null);
        verify(questionCallbackInboxDOMapper).updateConsumeStatus("cb-9501", "RECEIVED", "PROCESSED");
    }

    @Test
    void batchUpdateFailedQuestions_shouldPersistFailureReasonToTask() {
        AIProcessResultMessage message = new AIProcessResultMessage("701", "GENERATE", 0,
                null, "NA", "AI超时");
        message.setTaskId("9701");
        message.setCallbackKey("cb-9701");
        when(questionCallbackInboxDOMapper.selectByCallbackKey("cb-9701")).thenReturn(null);
        when(questionCallbackInboxDOMapper.insert(any(QuestionCallbackInboxDO.class))).thenReturn(1);
        when(questionDOMapper.selectByPrimaryKey(701L))
                .thenReturn(QuestionDO.builder().id(701L).processStatus("PROCESSING").build());
        when(questionProcessTaskDOMapper.selectByPrimaryKey(9701L))
                .thenReturn(QuestionProcessTaskDO.builder().id(9701L).questionId(701L).mode("GENERATE")
                        .attemptNo(1).taskStatus("DISPATCHED").build());
        when(questionProcessTaskDOMapper.selectActiveTaskByQuestionId(701L))
                .thenReturn(QuestionProcessTaskDO.builder().id(9701L).questionId(701L).mode("GENERATE")
                        .attemptNo(1).taskStatus("DISPATCHED").build());
        when(questionDOMapper.transitStatus(701L, "PROCESSING", "PROCESS_FAILED")).thenReturn(1);
        when(questionProcessTaskDOMapper.updateTaskStatus(9701L, "DISPATCHED", "FAILED", "AI超时")).thenReturn(1);
        when(distributedIdGeneratorRpcService.nextQuestionBankEntityId()).thenReturn("39701");
        when(questionCallbackInboxDOMapper.updateConsumeStatus("cb-9701", "RECEIVED", "PROCESSED")).thenReturn(1);

        int count = questionCallbackAppService.batchUpdateFailedQuestions(Map.of("701", message));

        assertEquals(1, count);
        verify(questionDOMapper).transitStatus(701L, "PROCESSING", "PROCESS_FAILED");
        verify(questionProcessTaskDOMapper).updateTaskStatus(9701L, "DISPATCHED", "FAILED", "AI超时");
        verify(questionCallbackInboxDOMapper).updateConsumeStatus("cb-9701", "RECEIVED", "PROCESSED");
    }

    @Test
    void batchUpdateSuccessQuestions_missingModesButValidationResultPresent_shouldFallbackToValidate() {
        AIProcessResultMessage message = new AIProcessResultMessage("801", null, 1,
                "AI建议答案", "FAIL", "建议人工复核");
        message.setTaskId("9801");
        message.setCallbackKey("cb-9801");
        when(questionCallbackInboxDOMapper.selectByCallbackKey("cb-9801")).thenReturn(null);
        when(questionCallbackInboxDOMapper.insert(any(QuestionCallbackInboxDO.class))).thenReturn(1);
        when(questionDOMapper.selectByPrimaryKey(801L))
                .thenReturn(QuestionDO.builder().id(801L).processStatus("PROCESSING").answer("原答案").build());
        when(questionProcessTaskDOMapper.selectByPrimaryKey(9801L))
                .thenReturn(QuestionProcessTaskDO.builder().id(9801L).questionId(801L)
                        .mode(null).attemptNo(1).taskStatus("DISPATCHED").build());
        when(questionProcessTaskDOMapper.selectActiveTaskByQuestionId(801L))
                .thenReturn(QuestionProcessTaskDO.builder().id(9801L).questionId(801L)
                        .mode(null).attemptNo(1).taskStatus("DISPATCHED").build());
        when(distributedIdGeneratorRpcService.nextQuestionBankEntityId()).thenReturn("39801", "19801");
        when(questionValidationRecordDOMapper.insert(any(QuestionValidationRecordDO.class))).thenReturn(1);
        when(questionDOMapper.transitStatusAndReviewMode(801L, "PROCESSING", "REVIEW_PENDING", "VALIDATE")).thenReturn(1);
        when(questionProcessTaskDOMapper.updateTaskStatus(9801L, "DISPATCHED", "SUCCEEDED", null)).thenReturn(1);
        when(questionCallbackInboxDOMapper.updateConsumeStatus("cb-9801", "RECEIVED", "PROCESSED")).thenReturn(1);

        int count = questionCallbackAppService.batchUpdateSuccessQuestions(Map.of("801", message));

        assertEquals(1, count);
        verify(questionValidationRecordDOMapper).insert(any(QuestionValidationRecordDO.class));
        verify(questionDOMapper).transitStatusAndReviewMode(801L, "PROCESSING", "REVIEW_PENDING", "VALIDATE");
        verify(questionDOMapper, never()).transitStatusAndAnswerAndReviewMode(any(), any(), any(), any(), any());
    }

    @Test
    void batchUpdateSuccessQuestions_missingModesAndNoCurrentAnswer_shouldFallbackToGenerate() {
        AIProcessResultMessage message = new AIProcessResultMessage("802", null, 1,
                "AI生成答案", "NA", null);
        message.setTaskId("9802");
        message.setCallbackKey("cb-9802");
        when(questionCallbackInboxDOMapper.selectByCallbackKey("cb-9802")).thenReturn(null);
        when(questionCallbackInboxDOMapper.insert(any(QuestionCallbackInboxDO.class))).thenReturn(1);
        when(questionDOMapper.selectByPrimaryKey(802L))
                .thenReturn(QuestionDO.builder().id(802L).processStatus("PROCESSING").answer(null).build());
        when(questionProcessTaskDOMapper.selectByPrimaryKey(9802L))
                .thenReturn(QuestionProcessTaskDO.builder().id(9802L).questionId(802L)
                        .mode(null).attemptNo(1).taskStatus("DISPATCHED").build());
        when(questionProcessTaskDOMapper.selectActiveTaskByQuestionId(802L))
                .thenReturn(QuestionProcessTaskDO.builder().id(9802L).questionId(802L)
                        .mode(null).attemptNo(1).taskStatus("DISPATCHED").build());
        when(distributedIdGeneratorRpcService.nextQuestionBankEntityId()).thenReturn("39802");
        when(questionDOMapper.transitStatusAndAnswerAndReviewMode(802L, "PROCESSING", "REVIEW_PENDING",
                "AI生成答案", "GENERATE")).thenReturn(1);
        when(questionProcessTaskDOMapper.updateTaskStatus(9802L, "DISPATCHED", "SUCCEEDED", null)).thenReturn(1);
        when(questionCallbackInboxDOMapper.updateConsumeStatus("cb-9802", "RECEIVED", "PROCESSED")).thenReturn(1);

        int count = questionCallbackAppService.batchUpdateSuccessQuestions(Map.of("802", message));

        assertEquals(1, count);
        verify(questionDOMapper).transitStatusAndAnswerAndReviewMode(802L, "PROCESSING", "REVIEW_PENDING",
                "AI生成答案", "GENERATE");
        verify(questionValidationRecordDOMapper, never()).insert(any(QuestionValidationRecordDO.class));
    }
}
