package com.zhoushuo.eaqb.excel.parser.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Excel预上传记录表
 * 用于存储文件上传阶段的校验信息和错误详情。
 * 当前实现中，该记录主要在校验失败时写入，用于后续按 preUploadId 查询错误明细。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExcelPreUploadRecordDO {

    /**
     * 预上传记录ID（分布式ID）
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 校验状态（SUCCESS/FAIL）
     */
    private String verifyStatus;

    /**
     * 详细错误信息（多个错误之间用换行符分隔）
     */
    private String errorMessages;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 最后更新时间
     */
    private LocalDateTime updateTime;
}
