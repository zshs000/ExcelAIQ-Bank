package com.zhoushuo.eaqb.question.bank.biz.consumer;

import com.zhoushuo.eaqb.question.bank.biz.constant.MQConstants;
import com.zhoushuo.eaqb.question.bank.biz.model.AIProcessResultMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;


import com.zhoushuo.eaqb.question.bank.biz.service.QuestionService;

/**
 * AI处理结果消费者，用于接收并处理AI服务返回的题目处理结果
 */
@Component
@Slf4j
@RocketMQMessageListener(topic = MQConstants.TOPIC_AI_RESULT, consumerGroup = MQConstants.CONSUMER_GROUP_RESULT)
public class AIProcessResultConsumer implements RocketMQListener<AIProcessResultMessage> {

    @Resource
    private QuestionService questionService;

    @Override
    public void onMessage(AIProcessResultMessage resultMessage) {
        log.info("收到AI处理结果消息: questionId={}, successFlag={}",
                resultMessage.getQuestionId(), resultMessage.getSuccessFlag());

        try {
            // 判断处理是否成功（successFlag为1表示成功）
            if (resultMessage.getSuccessFlag() != null && resultMessage.getSuccessFlag() == 1) {
                // 处理成功：更新题目状态为"待审查"
                log.info("题目处理成功: questionId={}, answer={}",
                        resultMessage.getQuestionId(), resultMessage.getAnswer());

                // 调用服务层更新题目状态
                questionService.updateQuestionStatusToReview(resultMessage.getQuestionId(), resultMessage.getAnswer());
            } else {
                // 处理失败：记录错误日志
                log.error("题目处理失败: questionId={}, errorMessage={}",
                        resultMessage.getQuestionId(), resultMessage.getErrorMessage());
                // 暂时只打印日志，后续可以扩展到错误信息表
            }
        } catch (Exception e) {
            log.error("处理AI结果消息时发生异常: questionId={}", resultMessage.getQuestionId(), e);
            // 异常处理，避免消息重试风暴
        }
    }
}