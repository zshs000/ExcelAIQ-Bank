package com.zhoushuo.eaqb.question.bank.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * 题目导入批次主表对象。
 * 对应“两阶段导入”链路中的批次级元数据与状态机落库快照。
 */
public class QuestionImportBatchDO {
    /**
     * 导入批次ID（全局唯一）。
     */
    private Long id;
    /**
     * 来源文件ID（对应上传文件记录）。
     */
    private Long fileId;
    /**
     * 发起导入的用户ID。
     */
    private Long userId;
    /**
     * 批次状态：APPENDING / READY / COMMITTED / FAILED / ABORTED。
     */
    private String status;
    /**
     * 分块大小（每个 chunk 期望承载的行数上限）。
     */
    private Integer chunkSize;
    /**
     * finish 阶段确认的期望 chunk 总数，仅 READY 及后续状态有值。
     */
    private Integer expectedChunkCount;
    /**
     * 已成功接收的 chunk 数。
     */
    private Integer receivedChunkCount;
    /**
     * 已成功接收的题目总行数（临时表累计行数）。
     */
    private Integer totalRowCount;
    /**
     * commit 成功写入正式题库的题目数量，仅 COMMITTED 状态有值。
     */
    private Integer importedCount;
    /**
     * 批次失败/中止原因说明。
     */
    private String errorMessage;
    /**
     * 创建时间。
     */
    private LocalDateTime createdTime;
    /**
     * 最后更新时间（状态流转或计数更新都会刷新）。
     */
    private LocalDateTime updatedTime;
}
