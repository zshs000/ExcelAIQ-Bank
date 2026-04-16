package com.zhoushuo.eaqb.question.bank.biz.service.impl.imports;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionImportTempDO;
import com.zhoushuo.eaqb.question.bank.req.AppendImportChunkRequestDTO;
import org.apache.commons.lang3.StringUtils;

/**
 * 分块幂等决策器。
 * 根据“是否已有历史 chunk 元信息 + 当前请求元信息是否一致”给出 ACCEPT/DUPLICATE/CONFLICT。
 */
public class ImportChunkDecisionService {

    /**
     * 决策规则：
     * 1. 历史不存在：ACCEPT；
     * 2. 历史存在且 rowCount/contentHash 一致：DUPLICATE；
     * 3. 历史存在但不一致：CONFLICT。
     */
    public ImportChunkDecision decide(AppendImportChunkRequestDTO request, QuestionImportTempDO existingChunk) {
        if (existingChunk == null) {
            return ImportChunkDecision.ACCEPT;
        }
        if (request.getRowCount().equals(existingChunk.getChunkRowCount())
                && StringUtils.equals(request.getContentHash(), existingChunk.getContentHash())) {
            return ImportChunkDecision.DUPLICATE;
        }
        return ImportChunkDecision.CONFLICT;
    }
}
