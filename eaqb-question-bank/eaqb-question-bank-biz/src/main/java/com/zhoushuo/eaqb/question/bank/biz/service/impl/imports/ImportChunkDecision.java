package com.zhoushuo.eaqb.question.bank.biz.service.impl.imports;

/**
 * 追加分块时的幂等判定结果。
 */
public enum ImportChunkDecision {
    /**
     * 当前 chunk 首次写入，允许落库。
     */
    ACCEPT,
    /**
     * 当前 chunk 为重复重试且内容一致，按幂等成功处理。
     */
    DUPLICATE,
    /**
     * 当前 chunk 为重复重试但内容不一致，按冲突处理并冻结批次。
     */
    CONFLICT
}
