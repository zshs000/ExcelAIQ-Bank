package com.zhoushuo.eaqb.question.bank.biz.consumer;

import com.zhoushuo.eaqb.question.bank.biz.constant.MQConstants;
import com.zhoushuo.eaqb.question.bank.biz.model.AIProcessResultMessage;
import com.zhoushuo.eaqb.question.bank.biz.utils.RedisUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI处理结果消费者，用于接收并处理AI服务返回的题目处理结果
 */
@Component
@Slf4j
@RocketMQMessageListener(topic = MQConstants.TOPIC_AI_RESULT, consumerGroup = MQConstants.CONSUMER_GROUP_RESULT)
public class AIProcessResultConsumer implements RocketMQListener<AIProcessResultMessage> {

    @Resource
    private RedisUtils redisUtils;

    // 本地缓存 - 一级缓存
    private final Map<String, AIProcessResultMessage> localCache = new ConcurrentHashMap<>();
    // 缓存计数，用于统计当前缓存的消息数量
    private final AtomicInteger cacheCount = new AtomicInteger(0);
    // 缓存阈值，当达到这个阈值时将数据写入Redis
    private static final int CACHE_THRESHOLD = 100;
    // Redis键前缀
    private static final String REDIS_KEY_PREFIX = "ai_result:";
    // Redis过期时间（分钟）
    private static final int REDIS_EXPIRE_MINUTES = 60;

    @Override
    public void onMessage(AIProcessResultMessage resultMessage) {
        log.info("收到AI处理结果消息: questionId={}, successFlag={}",
                resultMessage.getQuestionId(), resultMessage.getSuccessFlag());
        try {
            // 将消息存入本地缓存
            localCache.put(resultMessage.getQuestionId(), resultMessage);

            int currentCount = cacheCount.incrementAndGet();
            log.debug("本地缓存消息，当前缓存数量: {}", currentCount);

            // 检查是否达到阈值，达到则写入Redis
            if (currentCount >= CACHE_THRESHOLD) {
                log.info("本地缓存达到阈值，开始批量写入Redis，缓存数量: {}", currentCount);
                batchWriteToRedis();
            }
        } catch (Exception e) {
            log.error("处理AI结果消息时发生异常: questionId={}", resultMessage.getQuestionId(), e);
            // 异常处理，避免消息重试风暴
            try {
                // 发生异常时，至少将这条消息单独写入Redis
                String redisKey = REDIS_KEY_PREFIX + "single:" + resultMessage.getQuestionId();
                redisUtils.set(redisKey, resultMessage, REDIS_EXPIRE_MINUTES, TimeUnit.MINUTES);
                log.info("异常消息单独写入Redis: {}", redisKey);
            } catch (Exception redisEx) {
                log.error("异常消息写入Redis失败", redisEx);
            }
        }
    }

    /**
     * 批量将本地缓存写入Redis
     */
    private synchronized void batchWriteToRedis() {
        if (localCache.isEmpty()) {
            return;
        }

        try {
            // 生成批次ID
            String batchId = "batch_" + System.currentTimeMillis();
            String redisKey = REDIS_KEY_PREFIX + batchId;

            // 批量写入Redis Hash
            redisUtils.hPutAll(redisKey, localCache);
            // 设置过期时间
            redisUtils.expire(redisKey, REDIS_EXPIRE_MINUTES, TimeUnit.MINUTES);

            log.info("批量写入Redis成功，批次ID: {}, 消息数量: {}", batchId, localCache.size());

            // 清空本地缓存和计数
            localCache.clear();
            cacheCount.set(0);
        } catch (Exception e) {
            log.error("批量写入Redis失败", e);
            // 可以考虑使用备份策略或告警机制
        }
    }

    /**
     * 强制刷新本地缓存到Redis
     * 可用于系统关闭时或定时任务调用
     */
    public void flushCacheToRedis() {
        if (!localCache.isEmpty()) {
            log.info("强制刷新本地缓存到Redis，当前缓存数量: {}", localCache.size());
            batchWriteToRedis();
        }
    }
}