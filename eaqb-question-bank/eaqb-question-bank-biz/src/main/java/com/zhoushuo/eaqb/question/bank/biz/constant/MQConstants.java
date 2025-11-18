package com.zhoushuo.eaqb.question.bank.biz.constant;

/**
 * 消息队列相关常量
 */
public interface MQConstants {
    // 发送题目到AI处理的主题
    String TOPIC_TEST = "TestTopic";
    
    // AI处理结果返回的主题
    String TOPIC_AI_RESULT = "AIProcessResultTopic";


    // 消费者组
    String CONSUMER_GROUP_RESULT = "question-result-consumer-group";
}