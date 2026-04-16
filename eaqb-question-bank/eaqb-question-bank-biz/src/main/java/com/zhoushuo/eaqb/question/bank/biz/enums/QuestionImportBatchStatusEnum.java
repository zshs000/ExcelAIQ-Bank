package com.zhoushuo.eaqb.question.bank.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
/**
 * 题目导入批次状态枚举。
 * 一个导入任务对应一个批次，状态沿着 APPENDING -> READY -> COMMITTED 正向推进，
 * 异常路径会进入 FAILED 或 ABORTED 终态。
 */
public enum QuestionImportBatchStatusEnum {
    /**
     * 追加分块阶段：允许 append chunk。
     */
    APPENDING("APPENDING"),
    /**
     * 分块接收完成：允许 commit。
     */
    READY("READY"),
    /**
     * 已提交成功：临时数据已转正。
     */
    COMMITTED("COMMITTED"),
    /**
     * 失败终态：出现冲突/计数不一致等错误。
     */
    FAILED("FAILED"),
    /**
     * 中止终态：通常由清理任务或人工中止。
     */
    ABORTED("ABORTED");

    /**
     * 状态码（落库值）。
     */
    private final String code;
}
