package com.zhoushuo.eaqb.excel.parser.biz.service;

import com.zhoushuo.eaqb.excel.parser.biz.model.dto.ExcelFileUploadDTO;
import com.zhoushuo.framework.commono.response.Response;

public interface ExcelFileService {

    /**
     * 上传Excel文件
     * @param excelFileUploadDTO
     * @return
     */
    Response<?> uploadAExcel(ExcelFileUploadDTO excelFileUploadDTO);

    /**
     * 根据 preUploadId 查询校验失败明细。
     * 说明：preUploadId 仅在 uploadAExcel 校验失败时生成并返回。
     */
    Response<?> getValidationErrors(Long preUploadId);

    Response<?> parseExcelFileById(Long fileId);
}
