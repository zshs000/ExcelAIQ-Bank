package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.biz.model.AIProcessResultMessage;
import com.zhoushuo.eaqb.question.bank.biz.service.QuestionService;
import com.zhoushuo.eaqb.question.bank.biz.utils.RedisUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 批量处理服务，负责从Redis读取缓存的AI处理结果并批量更新数据库
 */
@Service
@Slf4j
public class BatchProcessorService {

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private QuestionService questionService;

    // Redis键前缀
    private static final String REDIS_KEY_PREFIX = "ai_result:";
    // 批量处理间隔时间（秒）
    private static final int PROCESS_INTERVAL_SECONDS = 300; // 5分钟
    // 单次处理的最大批次数量
    private static final int MAX_BATCHES_PER_PROCESS = 10;
    // 定时任务执行器
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * 初始化方法，启动定时任务
     */
    @PostConstruct
    public void init() {
        log.info("批量处理服务初始化，启动定时任务，处理间隔: {}秒", PROCESS_INTERVAL_SECONDS);
        // 启动定时任务，延迟10秒后开始执行，之后每隔指定时间执行一次
        scheduler.scheduleAtFixedRate(this::processBatches,
                10, PROCESS_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 处理Redis中的批次数据
     */
    public void processBatches() {
        try {
            log.info("开始执行批量处理任务");

            // 获取所有批次的键
            Set<String> batchKeys = (Set<String>) redisUtils.redisTemplate.keys(REDIS_KEY_PREFIX + "batch_*");
            if (batchKeys == null || batchKeys.isEmpty()) {
                log.info("Redis中没有待处理的批次数据");
                return;
            }

            // 限制单次处理的批次数量，避免一次处理太多数据
            List<String> keysToProcess = batchKeys.stream()
                    .limit(MAX_BATCHES_PER_PROCESS)
                    .collect(Collectors.toList());

            log.info("待处理批次数量: {}, 本次处理批次数量: {}", batchKeys.size(), keysToProcess.size());

            // 遍历处理每个批次
            for (String batchKey : keysToProcess) {
                processBatch(batchKey);
            }

            log.info("批量处理任务执行完成");
        } catch (Exception e) {
            log.error("批量处理任务执行异常", e);
        }
    }

    /**
     * 处理单个批次
     * @param batchKey 批次的Redis键
     */
    private void processBatch(String batchKey) {
        try {
            log.info("开始处理批次: {}", batchKey);

            // 获取批次中的所有数据
            Map<String, AIProcessResultMessage> batchData = redisUtils.hGetAll(batchKey);
            if (batchData == null || batchData.isEmpty()) {
                log.warn("批次 {} 为空，删除该批次", batchKey);
                redisUtils.delete(batchKey);
                return;
            }

            log.info("批次 {} 包含 {} 条数据", batchKey, batchData.size());

            // 分离成功和失败的结果
            Map<String, String> successResults = new HashMap<>();
            Map<String, String> errorResults = new HashMap<>();

            // 解析每个消息并分类
            for (Map.Entry<String, AIProcessResultMessage> entry : batchData.entrySet()) {
                try {
                    String questionId = entry.getKey();
                    AIProcessResultMessage message = entry.getValue();

                    // 根据successFlag分类
                    if (message.getSuccessFlag() != null && message.getSuccessFlag() == 1) {
                        // 成功结果
                        successResults.put(questionId, message.getAnswer());
                    } else {
                        // 失败结果
                        errorResults.put(questionId, message.getErrorMessage() != null ? 
                                message.getErrorMessage() : "处理失败，无具体错误信息");
                    }
                } catch (Exception e) {
                    log.error("解析批次数据时发生异常: key={}", entry.getKey(), e);
                }
            }

            // 批量更新成功的题目
            if (!successResults.isEmpty()) {
                int successCount = questionService.batchUpdateSuccessQuestions(successResults);
                log.info("批量更新成功题目完成，计划更新: {}, 实际更新: {}", 
                        successResults.size(), successCount);
            }

            // 批量更新失败的题目
            if (!errorResults.isEmpty()) {
                int errorCount = questionService.batchUpdateFailedQuestions(errorResults);
                log.info("批量更新失败题目完成，计划更新: {}, 实际更新: {}", 
                        errorResults.size(), errorCount);
            }

            // 处理完成后删除该批次
            redisUtils.delete(batchKey);
            log.info("批次 {} 处理完成并删除", batchKey);

        } catch (Exception e) {
            log.error("处理批次 {} 时发生异常", batchKey, e);
            // 不删除失败的批次，让下次任务继续尝试
        }
    }

    /**
     * 手动触发批量处理（可用于测试或紧急处理）
     */
    public void manualTriggerProcess() {
        log.info("手动触发批量处理");
        processBatches();
    }

    /**
     * 清理资源
     */
    public void shutdown() {
        log.info("批量处理服务关闭，停止定时任务");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}