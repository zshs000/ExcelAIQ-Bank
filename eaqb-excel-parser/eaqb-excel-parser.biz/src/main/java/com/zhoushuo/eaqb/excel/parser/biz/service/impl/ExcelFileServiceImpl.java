package com.zhoushuo.eaqb.excel.parser.biz.service.impl;

import com.zhoushuo.eaqb.excel.parser.biz.model.dto.ExcelFileUploadDTO;
import com.zhoushuo.eaqb.excel.parser.biz.service.ExcelFileService;
import com.zhoushuo.eaqb.excel.parser.biz.service.app.ExcelParseAppService;
import com.zhoushuo.eaqb.excel.parser.biz.service.app.ExcelUploadAppService;
import com.zhoushuo.eaqb.excel.parser.biz.service.app.ExcelValidationQueryAppService;
import com.zhoushuo.framework.common.response.Response;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * Excel 文件门面服务。
 *
 * 保留原有对外接口不变，把具体业务分别委托给上传、错误查询、解析导入三个应用服务，
 * 避免单个实现类继续膨胀成上帝类。
 */
@Service
public class ExcelFileServiceImpl implements ExcelFileService {

    @Resource
    private ExcelUploadAppService excelUploadAppService;
    @Resource
    private ExcelValidationQueryAppService excelValidationQueryAppService;
    @Resource
    private ExcelParseAppService excelParseAppService;

    @Override
    public Response<?> uploadAExcel(ExcelFileUploadDTO excelFileUploadDTO) {
        return excelUploadAppService.uploadAExcel(excelFileUploadDTO);
    }

    @Override
    public Response<?> getValidationErrors(Long preUploadId) {
        return excelValidationQueryAppService.getValidationErrors(preUploadId);
    }

    @Override
    public Response<?> parseExcelFileById(Long fileId) {
        return excelParseAppService.parseExcelFileById(fileId);
    }
}
