package com.zhoushuo.eaqb.excel.parser.biz.service.app;

import com.zhoushuo.eaqb.excel.parser.biz.domain.dataobject.ExcelPreUploadRecordDO;
import com.zhoushuo.eaqb.excel.parser.biz.domain.mapper.ExcelPreUploadRecordDOMapper;
import com.zhoushuo.eaqb.excel.parser.biz.enums.ResponseCodeEnum;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.common.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcelValidationQueryAppServiceTest {

    @Mock
    private ExcelPreUploadRecordDOMapper excelPreUploadRecordDOMapper;

    @InjectMocks
    private ExcelValidationQueryAppService excelValidationQueryAppService;

    @AfterEach
    void tearDown() {
        LoginUserContextHolder.remove();
    }

    @Test
    void getValidationErrors_shouldReturnErrorListForOwner() {
        LoginUserContextHolder.setUserId(123L);
        ExcelPreUploadRecordDO record = ExcelPreUploadRecordDO.builder()
                .id(7001L)
                .userId(123L)
                .errorMessages("第2行错误\n第3行错误")
                .build();
        when(excelPreUploadRecordDOMapper.selectById(7001L)).thenReturn(record);

        Response<?> response = excelValidationQueryAppService.getValidationErrors(7001L);

        assertTrue(response.isSuccess());
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) response.getData();
        assertEquals(2, errors.size());
        assertEquals("第2行错误", errors.get(0));
        assertEquals("第3行错误", errors.get(1));
    }

    @Test
    void getValidationErrors_whenRecordBelongsToAnotherUser_shouldReturnNoPermission() {
        LoginUserContextHolder.setUserId(123L);
        ExcelPreUploadRecordDO record = ExcelPreUploadRecordDO.builder()
                .id(7002L)
                .userId(999L)
                .errorMessages("第2行错误")
                .build();
        when(excelPreUploadRecordDOMapper.selectById(7002L)).thenReturn(record);

        Response<?> response = excelValidationQueryAppService.getValidationErrors(7002L);

        assertFalse(response.isSuccess());
        assertEquals(ResponseCodeEnum.NO_PERMISSION.getErrorCode(), response.getErrorCode());
    }
}
