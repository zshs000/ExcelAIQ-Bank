package com.zhoushuo.eaqb.question.bank.biz.config;

import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;



@Configuration
@ConditionalOnProperty(prefix = "feature.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import(RocketMQAutoConfiguration.class)
public class RocketMQConfig {
}
