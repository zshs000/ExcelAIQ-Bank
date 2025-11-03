package com.zhoushuo.eaqb.excel.parser.biz.controller;


import com.zhoushuo.eaqb.excel.parser.biz.model.dto.ExcelFileUploadDTO;
import com.zhoushuo.eaqb.excel.parser.biz.service.ExcelFileService;
import com.zhoushuo.framework.biz.operationlog.aspect.ApiOperationLog;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/excel-parser")  // 基础路径使用功能模块名的短横线形式
@Slf4j
public class ExcelFileController {

    // 注入相关服务
     @Resource
     private ExcelFileService excelFileService;

    /**
     * 上传Excel文件
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    //@ApiOperationLog(description = "上传Excel文件")
    public Response<?> uploadAndParseExcel(@Validated ExcelFileUploadDTO excelFileUploadDTO/* 相应的请求参数类 */) {

        return excelFileService.uploadAExcel(excelFileUploadDTO);
    }

    /**
     * 获取Excel文件验证错误详情
     * 通过预上传ID查询详细的验证错误信息
     *
     * @param preUploadId 预上传记录ID
     * @return 详细错误信息列表
     */
    @GetMapping("/validation-errors")
    @ApiOperationLog( description = "通过预上传ID获取Excel文件的详细验证错误信息")
    public Response<?> getValidationErrors(@RequestParam("preUploadId") Long preUploadId) {
        return excelFileService.getValidationErrors(preUploadId);
    }

    /**
     * 查询文件列表
     */
//    @PostMapping("/list")
//    public Response<?> getFileList(@Validated @RequestBody /* 相应的请求参数类 */) {
//        // 实现逻辑
//        return Response.success();
//    }

    // 其他相关接口...
}