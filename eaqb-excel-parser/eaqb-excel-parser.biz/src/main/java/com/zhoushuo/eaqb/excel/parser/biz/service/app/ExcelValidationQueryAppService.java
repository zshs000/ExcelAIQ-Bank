package com.zhoushuo.eaqb.excel.parser.biz.service.app;

import com.zhoushuo.eaqb.excel.parser.biz.domain.dataobject.ExcelPreUploadRecordDO;
import com.zhoushuo.eaqb.excel.parser.biz.domain.mapper.ExcelPreUploadRecordDOMapper;
import com.zhoushuo.eaqb.excel.parser.biz.enums.ResponseCodeEnum;
import com.zhoushuo.framework.biz.context.holder.LoginUserContextHolder;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 预上传错误查询应用服务。
 *
 * 负责根据 preUploadId 返回模板校验失败时落库的完整错误明细。
 */
@Service
public class ExcelValidationQueryAppService {

    @Resource
    private ExcelPreUploadRecordDOMapper excelPreUploadRecordDOMapper;

    public Response<?> getValidationErrors(Long preUploadId) {
        if (preUploadId == null) {
            return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID);
        }

        ExcelPreUploadRecordDO record = excelPreUploadRecordDOMapper.selectById(preUploadId);
        if (record == null) {
            return Response.fail(ResponseCodeEnum.RECORD_NOT_FOUND);
        }

        Long currentUserId = LoginUserContextHolder.getUserId();
        if (!record.getUserId().equals(currentUserId)) {
            return Response.fail(ResponseCodeEnum.NO_PERMISSION);
        }

        List<String> errorList = Arrays.asList(record.getErrorMessages().split("\\n"));
        return Response.success(errorList);
    }
}
