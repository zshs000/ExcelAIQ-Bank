package com.zhoushuo.eaqb.question.bank.biz.consumer;

import com.zhoushuo.eaqb.question.bank.biz.model.AIProcessResultMessage;
import com.zhoushuo.eaqb.question.bank.biz.service.QuestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIProcessResultConsumerTest {

    @Mock
    private QuestionService questionService;

    @InjectMocks
    private AIProcessResultConsumer consumer;

    @Test
    void onMessage_failedResult_shouldPassWholeMessageToFailureHandler() {
        AIProcessResultMessage message = new AIProcessResultMessage("701", "GENERATE", 0,
                null, "NA", "AI超时");
        message.setTaskId("9701");
        when(questionService.batchUpdateFailedQuestions(anyMap())).thenReturn(1);

        consumer.onMessage(message);

        verify(questionService).batchUpdateFailedQuestions(Map.of("701", message));
    }

    @Test
    void onMessage_serviceThrows_shouldRethrowForMqRetry() {
        AIProcessResultMessage message = new AIProcessResultMessage("702", "GENERATE", 1,
                "AI答案", "NA", null);
        message.setTaskId("9702");
        when(questionService.batchUpdateSuccessQuestions(anyMap())).thenThrow(new IllegalStateException("db down"));

        assertThrows(IllegalStateException.class, () -> consumer.onMessage(message));
    }
}
