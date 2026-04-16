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
 * 题目导入临时明细对象（按行存储）。
 * 一个 chunk 对应多条临时行记录，通过 batchId + chunkNo 聚合识别。
 */
public class QuestionImportTempDO {
    /**
     * 临时明细主键ID（自增）。
     */
    private Long id;
    /**
     * 所属导入批次ID。
     */
    private Long batchId;
    /**
     * 分块序号（从 1 开始）。
     */
    private Integer chunkNo;
    /**
     * 分块内行号（从 1 开始）。
     */
    private Integer rowNo;
    /**
     * 当前 chunk 原始行数（同一 chunk 的每一行会重复保存该值）。
     */
    private Integer chunkRowCount;
    /**
     * 当前 chunk 内容哈希（同一 chunk 的每一行会重复保存该值）。
     */
    private String contentHash;
    /**
     * 题干内容。
     */
    private String content;
    /**
     * 答案内容。
     */
    private String answer;
    /**
     * 解析内容。
     */
    private String analysis;
    /**
     * 临时记录创建时间。
     */
    private LocalDateTime createdTime;
}
