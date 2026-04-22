package com.zhoushuo.eaqb.question.bank.biz.consumer;

import com.zhoushuo.eaqb.question.bank.biz.constant.MQConstants;
import com.zhoushuo.eaqb.question.bank.biz.model.AIProcessResultMessage;
import com.zhoushuo.eaqb.question.bank.biz.service.QuestionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AI处理结果消费者，用于接收并处理AI服务返回的题目处理结果
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "feature.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(topic = MQConstants.TOPIC_AI_RESULT, consumerGroup = MQConstants.CONSUMER_GROUP_RESULT)
public class AIProcessResultConsumer implements RocketMQListener<AIProcessResultMessage> {

    @Resource
    private QuestionService questionService;

    @Override
    public void onMessage(AIProcessResultMessage resultMessage) {
        if (resultMessage == null || StringUtils.isBlank(resultMessage.getQuestionId())) {
            log.warn("收到无效AI处理结果消息，已忽略: {}", resultMessage);
            return;
        }

        log.info("收到AI处理结果消息: questionId={}, successFlag={}",
                resultMessage.getQuestionId(), resultMessage.getSuccessFlag());

        try {
            if (Integer.valueOf(1).equals(resultMessage.getSuccessFlag())) {
                int updatedCount = questionService.batchUpdateSuccessQuestions(
                        Map.of(resultMessage.getQuestionId(), resultMessage)
                );
                log.info("AI成功消息处理完成，questionId={}, updatedCount={}",
                        resultMessage.getQuestionId(), updatedCount);
                return;
            }

            int updatedCount = questionService.batchUpdateFailedQuestions(
                    Map.of(resultMessage.getQuestionId(), resultMessage)
            );
            log.info("AI失败消息处理完成，questionId={}, updatedCount={}",
                    resultMessage.getQuestionId(), updatedCount);
        } catch (Exception e) {
            log.error("处理AI结果消息失败，questionId={}", resultMessage.getQuestionId(), e);
            throw new IllegalStateException("处理AI结果消息失败", e);
        }
    }
}
