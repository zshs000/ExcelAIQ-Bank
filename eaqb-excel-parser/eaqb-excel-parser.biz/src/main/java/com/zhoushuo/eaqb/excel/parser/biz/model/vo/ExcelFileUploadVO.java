package com.zhoushuo.eaqb.excel.parser.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Excel文件上传响应VO
 * 用于返回上传文件的相关信息给前端（不包含直接的OSS链接）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExcelFileUploadVO {

    /**
     * 文件ID（最重要的标识，前端后续操作都通过此ID进行）
     */
    private Long fileId;

    /**
     * 文件名（用于前端显示）
     */
    private String fileName;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 上传时间
     */
    private LocalDateTime uploadTime;

    /**
     * 文件状态（UPLOADED, PARSING, PARSED, FAILED等）
     */
    private String status;

    /**
     * 文件大小格式化显示（如：1.5MB，便于前端直接展示）
     */
    private String formattedSize;
    // 是否删除标识 - 默认false（未删除）
    private Boolean isDeleted = false;

    // 删除时间 - 仅当isDeleted为true时有意义
    private LocalDateTime deletedTime;

    // 新增字段用于错误处理
    private Long preUploadId;
    private String verifyStatus; // 校验状态: SUCCESS, FAIL
    private String errorSummary; // 错误摘要
}