package com.zhoushuo.eaqb.question.bank.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class BatchImportQuestionResponseDTO {
    // 导入是否成功
    private boolean success;
    // 总题目数量
    private int totalCount;
    // 成功数量
    private int successCount;
    // 失败数量
    private int failedCount;
    // 错误信息（仅在整体失败时提供）
    private String errorMessage;
    // 错误类型（可选，用于前端展示不同的错误提示样式）
    private String errorType;

}
