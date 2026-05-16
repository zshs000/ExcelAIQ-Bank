package com.zhoushuo.eaqb.excel.parser.biz.util;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import java.util.*;

/**
 * Excel 模板与内容校验监听器（上传阶段使用）。
 *
 * 处理方式：
 * 1. 基于 EasyExcel 的事件回调按行处理，避免一次性加载整表。
 * 2. 发现单行错误时不中断，持续收集后续行错误，便于一次性反馈。
 * 3. 错误总量设上限，防止超大文件导致错误列表无限膨胀。
 */
@Slf4j
public class ExcelTemplateValidator extends AnalysisEventListener<Map<Integer, String>> {

    // 错误明细最大保留条数，超出后使用“已截断”提示替换最后一条。
    private static final int MAX_ERROR_MESSAGES = 200;
    private final List<String> requiredHeaders = Arrays.asList("题目", "答案", "解析");
    private final List<String> errorMessages = new ArrayList<>();
    // 上传模板约定第 1 行必须是表头，不接受前置空行或其他占位内容。
    private boolean headerChecked = false;
    // 当前处理到的 Excel 行号（从 1 开始）。
    private int currentRowNum = 0;
    // 标记是否已经发生错误截断，避免重复写入截断提示。
    private boolean errorTruncated = false;

    @Override
    public void invoke(Map<Integer, String> data, AnalysisContext context) {
        currentRowNum = context.readRowHolder().getRowIndex() + 1;

        // 将Map转换为List（保持列顺序）
        List<String> rowData = convertMapToOrderedList(data);

        log.info("处理第{}行数据: {}", currentRowNum, rowData);

        // 跳过空行
        if (isBlankRow(rowData)) {
            // 当前策略：空行不报错，直接忽略。
            return;
        }

        // 第一个被读取到的非空行必须就是第 1 行表头。
        if (!headerChecked) {
            if (currentRowNum != 1) {
                addError("第1行必须是表头，不允许前置空行或其他内容");
            }
            checkHeaders(rowData, currentRowNum);
            headerChecked = true;
            return;
        }

        // 校验内容行
        validateContent(rowData);
    }
    /**
     * 将Map按列顺序转换为List
     * EasyExcel 在该模式下按列索引返回 Map，这里统一转成顺序数组便于后续规则校验。
     */
    private List<String> convertMapToOrderedList(Map<Integer, String> dataMap) {
        if (dataMap == null || dataMap.isEmpty()) {
            return new ArrayList<>();
        }

        // 获取最大列索引
        int maxColumnIndex = dataMap.keySet().stream()
                .max(Integer::compareTo)
                .orElse(-1);

        List<String> result = new ArrayList<>();
        for (int i = 0; i <= maxColumnIndex; i++) {
            result.add(dataMap.getOrDefault(i, ""));
        }

        return result;
    }

    /**
     * 严格检查标题行
     * 约定第 1-3 列必须严格为：题目、答案、解析。
     */
    private void checkHeaders(List<String> headers, int rowNum) {
        if (headers == null || headers.size() < 3) {
            addError(String.format("第%d行: 标题行缺少必要的列，必须是：题目、答案、解析", rowNum));
            return;
        }

        for (int i = 0; i < requiredHeaders.size(); i++) {
            String expected = requiredHeaders.get(i);
            String actual = i < headers.size() ? headers.get(i) : "";

            if (!expected.equals(actual)) {
                addError(String.format("第%d行第%d列: 标题应该是'%s'，实际是'%s'",
                        rowNum, i + 1, expected, actual));
            }
        }
    }

    /**
     * 校验内容行
     * 当前规则包含：
     * - 题目必填；
     * - 内容不允许包含空格；
     * - 有解析时必须有答案；
     * - 不允许出现多余非空列。
     */
    private void validateContent(List<String> data) {
        // 题目列不能为空
        if (data.size() < 1 || StringUtils.isBlank(data.get(0))) {
            addError(String.format("第%d行第1列: 题目不能为空", currentRowNum));
        } else {
            // 检查题目中不能有空格
            checkNoSpaces(data.get(0), currentRowNum, 1, "题目");
        }

        // 检查答案列的空格
        if (data.size() > 1 && StringUtils.isNotBlank(data.get(1))) {
            checkNoSpaces(data.get(1), currentRowNum, 2, "答案");
        }

        // 检查解析列的空格和依赖关系
        if (data.size() > 2) {
            String answer = data.size() > 1 ? data.get(1) : "";
            String analysis = data.get(2);

            // 有解析时必须要有答案
            if (StringUtils.isNotBlank(analysis) && StringUtils.isBlank(answer)) {
                addError(String.format("第%d行: 有解析时必须要有答案", currentRowNum));
            }

            // 检查解析中的空格
            if (StringUtils.isNotBlank(analysis)) {
                checkNoSpaces(analysis, currentRowNum, 3, "解析");
            }
        }

        // 检查是否有超出预期列数的数据
        if (data.size() > 3) {
            for (int i = 3; i < data.size(); i++) {
                if (StringUtils.isNotBlank(data.get(i))) {
                    addError(String.format("第%d行第%d列: 发现额外数据，请删除多余列",
                            currentRowNum, i + 1));
                }
            }
        }
    }

    /**
     * 检查字符串中不能包含空格
     */
    private void checkNoSpaces(String content, int rowNum, int colNum, String fieldName) {
        if (content.contains(" ")) {
            addError(String.format("第%d行第%d列(%s): 不能包含空格",
                    rowNum, colNum, fieldName));
        }
    }

    /**
     * 判断是否空行（全部单元格都为空）
     */
    private boolean isBlankRow(List<String> data) {
        if (data == null || data.isEmpty()) return true;
        for (String cell : data) {
            if (StringUtils.isNotBlank(cell)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // 文件仅有标题行时，补充一个总结性错误提示。
        if (headerChecked && currentRowNum <= 1) {
            addError("文件没有数据内容，请至少添加一行题目");
        }
    }

    /**
     * 统一错误收集入口：错误条数最多保留 MAX_ERROR_MESSAGES 条。
     * 超过上限时，将最后一条替换为“已截断”提示，避免错误明细无限膨胀。
     */
    private void addError(String message) {
        if (!errorTruncated && errorMessages.size() < MAX_ERROR_MESSAGES) {
            errorMessages.add(message);
            return;
        }
        if (!errorTruncated) {
            errorMessages.set(MAX_ERROR_MESSAGES - 1,
                    String.format("错误数量过多，已截断展示（最多%d条）", MAX_ERROR_MESSAGES));
            errorTruncated = true;
        }
    }

    /**
     * 获取校验结果
     */
    public boolean isValid() {
        return errorMessages.isEmpty();
    }

    /**
     * 获取错误信息
     */
    public List<String> getErrorMessages() {
        return new ArrayList<>(errorMessages);
    }

    /**
     * 获取格式化的错误报告
     */
    public String getFormattedErrorReport() {
        if (errorMessages.isEmpty()) {
            return "✅ 文件格式正确，没有发现错误";
        }

        StringBuilder report = new StringBuilder();
        report.append("❌ 发现 ").append(errorMessages.size()).append(" 个错误：\n\n");

        for (int i = 0; i < errorMessages.size(); i++) {
            report.append(i + 1).append(". ").append(errorMessages.get(i)).append("\n");
        }

        report.append("\n修改建议：\n");
        report.append("1. 确保标题行严格为：题目、答案、解析\n");
        report.append("2. 所有内容中不能包含空格\n");
        report.append("3. 题目不能为空\n");
        report.append("4. 有解析时必须有答案\n");
        report.append("5. 删除多余的数据列\n");

        return report.toString();
    }
}
