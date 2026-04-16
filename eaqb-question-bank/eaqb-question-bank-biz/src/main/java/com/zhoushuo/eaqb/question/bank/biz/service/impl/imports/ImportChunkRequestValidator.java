package com.zhoushuo.eaqb.question.bank.biz.service.impl.imports;

import com.zhoushuo.eaqb.question.bank.biz.enums.ResponseCodeEnum;
import com.zhoushuo.eaqb.question.bank.req.AppendImportChunkRequestDTO;
import com.zhoushuo.eaqb.question.bank.util.ImportChunkHashUtil;
import com.zhoushuo.framework.commono.exception.BizException;
import org.apache.commons.lang3.StringUtils;

/**
 * appendImportChunk 入参校验器。
 * 只负责“请求结构与基础一致性”校验，不负责业务状态校验（如批次归属、批次状态）。
 */
public class ImportChunkRequestValidator {

    /**
     * 校验项：
     * 1. request / batchId / chunkNo / rowCount 非空；
     * 2. hashVersion 非空且在支持版本列表内；
     * 3. contentHash 非空；
     * 4. rows 非空且至少 1 行；
     * 5. rowCount 与 rows.size() 一致。
     * 任一条件不满足，抛 PARAM_NOT_VALID。
     */
    public void validate(AppendImportChunkRequestDTO request) {
        if (request == null || request.getBatchId() == null || request.getChunkNo() == null
                || request.getRowCount() == null || StringUtils.isBlank(request.getHashVersion())
                || !ImportChunkHashUtil.isSupportedHashVersion(request.getHashVersion())
                || StringUtils.isBlank(request.getContentHash())
                || request.getRows() == null || request.getRows().isEmpty()
                || request.getRowCount() != request.getRows().size()) {
            throw new BizException(ResponseCodeEnum.PARAM_NOT_VALID);
        }
    }
}
