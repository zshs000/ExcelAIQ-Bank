package com.zhoushuo.eaqb.excel.parser.biz.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
@Data
public class ExcelProcessVO {
    private static final long serialVersionUID = 1L;

    private String processStatus;    // success, failed
    private int totalCount;          // 总记录数
    private int successCount;        // 成功处理数
    private int failCount;           // 失败记录数
    private long processTimeMs;      // 处理耗时
    private String errorMessage;     // 简单错误信息
    private String fileId;
    private Long finishTime;
}

