package com.zhoushuo.eaqb.question.bank.req;

import lombok.Data;

@Data
/**
 * 结束导入批次（finish 阶段）请求。
 * 调用方在分块追加完成后上报预期计数，用于服务端对账并流转 READY。
 */
public class FinishImportBatchRequestDTO {
    /**
     * 导入批次ID。
     */
    private Long batchId;
    /**
     * 调用方统计的预期 chunk 总数。
     */
    private Integer expectedChunkCount;
    /**
     * 调用方统计的预期题目总行数。
     */
    private Integer expectedRowCount;
}
