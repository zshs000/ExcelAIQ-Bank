package com.zhoushuo.eaqb.excel.parser.biz.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ExcelFileUploadDTO {

    /**
     * 上传的Excel文件
     * required = true表示必填
     */
    @NotNull(message = "文件不能为空")
    private MultipartFile file;
}
