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


    Response<?> getValidationErrors(Long preUploadId);

    Response<?> parseExcelFileById(Long fileId);
}
