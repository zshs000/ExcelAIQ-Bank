package com.zhoushuo.eaqb.excel.parser.biz.service.impl;

import com.zhoushuo.eaqb.excel.parser.biz.model.dto.ExcelFileUploadDTO;
import com.zhoushuo.eaqb.excel.parser.biz.service.app.ExcelParseAppService;
import com.zhoushuo.eaqb.excel.parser.biz.service.app.ExcelUploadAppService;
import com.zhoushuo.eaqb.excel.parser.biz.service.app.ExcelValidationQueryAppService;
import com.zhoushuo.framework.common.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

/**
 * ExcelFileServiceImpl 门面测试。
 *
 * 当前类已经不再承载上传、查询、解析的具体实现，
 * 因此这里只验证三件事：
 * 1. 是否把请求委托给正确的应用服务；
 * 2. 是否把下游返回值原样透传；
 * 3. 是否不再掺杂自己的额外业务逻辑。
 */
@ExtendWith(MockitoExtension.class)
class ExcelFileServiceImplTest {

    @Mock
    private ExcelUploadAppService excelUploadAppService;
    @Mock
    private ExcelValidationQueryAppService excelValidationQueryAppService;
    @Mock
    private ExcelParseAppService excelParseAppService;

    @InjectMocks
    private ExcelFileServiceImpl excelFileService;

    @Test
    void uploadAExcel_shouldDelegateToUploadAppService() {
        ExcelFileUploadDTO dto = new ExcelFileUploadDTO();
        Response<String> expected = Response.success("upload-ok");
        doReturn(expected).when(excelUploadAppService).uploadAExcel(dto);

        Response<?> actual = excelFileService.uploadAExcel(dto);

        assertSame(expected, actual);
        verify(excelUploadAppService).uploadAExcel(dto);
    }

    @Test
    void getValidationErrors_shouldDelegateToValidationQueryAppService() {
        Long preUploadId = 7001L;
        Response<String> expected = Response.success("validation-errors");
        doReturn(expected).when(excelValidationQueryAppService).getValidationErrors(preUploadId);

        Response<?> actual = excelFileService.getValidationErrors(preUploadId);

        assertSame(expected, actual);
        verify(excelValidationQueryAppService).getValidationErrors(preUploadId);
    }

    @Test
    void parseExcelFileById_shouldDelegateToParseAppService() {
        Long fileId = 9001L;
        Response<String> expected = Response.success("parse-ok");
        doReturn(expected).when(excelParseAppService).parseExcelFileById(fileId);

        Response<?> actual = excelFileService.parseExcelFileById(fileId);

        assertSame(expected, actual);
        verify(excelParseAppService).parseExcelFileById(fileId);
    }
}
