package com.zhoushuo.eaqb.question.bank.biz.service.impl.imports;

import com.zhoushuo.eaqb.question.bank.req.AppendImportChunkRequestDTO;
import com.zhoushuo.eaqb.question.bank.util.ImportChunkHashUtil;
import org.apache.commons.lang3.StringUtils;

/**
 * 分块哈希校验器。
 * 下游基于请求中的 hashVersion 与 rows 重算哈希，并与 contentHash 比较。
 */
public class ImportChunkHashValidator {

    /**
     * 返回 true 表示请求体内容与 contentHash 一致。
     */
    public boolean isPayloadHashMatched(AppendImportChunkRequestDTO request) {
        String computedHash = ImportChunkHashUtil.computeHash(request.getHashVersion(), request.getRows());
        return StringUtils.equals(computedHash, request.getContentHash());
    }
}
