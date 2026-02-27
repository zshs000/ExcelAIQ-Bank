package com.zhoushuo.eaqb.excel.parser.biz.util;
//import com.alibaba.excel.context.AnalysisContext;
//import com.alibaba.excel.event.AnalysisEventListener;
//import com.alibaba.excel.metadata.CellExtra;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.lang3.StringUtils;
//import java.util.*;
//import java.util.regex.Pattern;
//@Slf4j
//public class ExcelTemplateValidator extends AnalysisEventListener<List<String>> {
//
//    // 预期的标题行
//    private final List<String> expectedHeaders = Arrays.asList("题目", "答案", "解析");
//
//    // 存储校验错误信息
//    private List<String> errorMessages = new ArrayList<>();
//
//    // 标记是否已检查标题行
//    private boolean headerChecked = false;
//
//    // 用于检测所有空白字符的正则表达式
//    private static final Pattern ALL_WHITESPACE_PATTERN = Pattern.compile("\\s+");
//    // 用于检测首尾空白字符的正则表达式
//    private static final Pattern LEADING_TRAILING_WHITESPACE_PATTERN = Pattern.compile("^\\s|\\s$");
//
//    // 当前行号
//    private int currentRowNum = 0;
//
//    // 跟踪已处理的行号，用于检测空行
//    private Set<Integer> processedRows = new HashSet<>();
//
//
//    /**
//     * 异常处理方法，在数据处理过程中发生异常时被调用
//     * @param exception 发生的异常对象
//     * @param context 分析上下文环境
//     * @throws Exception 可能抛出的异常
//     */
//    @Override
//    public void onException(Exception exception, AnalysisContext context) throws Exception {
//        super.onException(exception, context);
//    }
//
//    /**
//     * 数据处理的核心方法，用于处理解析到的数据行
//     * @param
//     * @param
//     */
//    @Override
//    public void invoke(List<String> data, AnalysisContext context) {
//        log.info("开始处理文件");
//        currentRowNum = context.readRowHolder().getRowIndex() + 1; // 行号从1开始
//        processedRows.add(currentRowNum);
//
//        // 检查标题行
//        if (!headerChecked) {
//            validateHeaderRow(data, currentRowNum);
//            headerChecked = true;
//            return; // 标题行不进行内容校验
//        }
//
//        // 检查是否为空行
//        if (isAllBlank(data)) {
//            errorMessages.add(String.format("第%d行: 不允许有空行", currentRowNum));
//            return;
//        }
//
//        // 校验内容
//        validateContentRow(data, currentRowNum);
//    }
//    // 校验标题行
//    private void validateHeaderRow(List<String> headers, int rowNum) {
//        if (headers == null || headers.size() < expectedHeaders.size()) {
//            errorMessages.add(String.format("第%d行: 标题行缺少必要的列", rowNum));
//            return;
//        }
//        for (int i = 0; i < expectedHeaders.size(); i++) {
//            String expectedHeader = expectedHeaders.get(i);
//            String actualHeader = headers.size() > i ? headers.get(i) : "";
//            // 严格匹配标题，不允许有任何偏差
//            if (!expectedHeader.equals(actualHeader)) {
//                errorMessages.add(String.format("第%d行第%d列: 标题应该是'%s'，实际是'%s'",
//                        rowNum, i + 1, expectedHeader, actualHeader));
//            }
//        }
//    }
//    // 校验内容行
//    private void validateContentRow(List<String> data, int rowNum) {
//        // 1. 校验题目列（第一列）
//        if (data == null || data.isEmpty() || StringUtils.isBlank(data.get(0))) {
//            errorMessages.add(String.format("第%d行第1列: 题目内容不能为空", rowNum));
//        } else {
//            // 检查题目内容中的空格
//            validateNoWhitespace(data.get(0), rowNum, 1, "题目");
//        }
//
//        // 2. 校验答案列（第二列）
//        if (data.size() > 1 && StringUtils.isNotBlank(data.get(1))) {
//            validateNoWhitespace(data.get(1), rowNum, 2, "答案");
//        }
//
//        // 3. 校验解析列（第三列）
//        if (data.size() > 2) {
//            // 依赖规则：如果答案为空，解析也必须为空
//            String answer = data.size() > 1 ? data.get(1) : "";
//            String analysis = data.get(2);
//
//            if (StringUtils.isBlank(answer) && StringUtils.isNotBlank(analysis)) {
//                errorMessages.add(String.format("第%d行: 当答案为空时，解析也必须为空", rowNum));
//            } else if (StringUtils.isNotBlank(analysis)) {
//                validateNoWhitespace(analysis, rowNum, 3, "解析");
//            }
//        }
//    }
//
//    // 校验内容中不包含空格
//    private void validateNoWhitespace(String content, int rowNum, int colNum, String fieldName) {
//        // 检查是否包含任何空白字符
//        if (ALL_WHITESPACE_PATTERN.matcher(content).find()) {
//            errorMessages.add(String.format("第%d行第%d列(%s): 内容中不允许包含空格字符",
//                    rowNum, colNum, fieldName));
//        }
//
//        // 检查首尾是否包含空白字符（冗余检查，确保完整性）
//        if (LEADING_TRAILING_WHITESPACE_PATTERN.matcher(content).find()) {
//            errorMessages.add(String.format("第%d行第%d列(%s): 内容首尾不允许包含空白字符",
//                    rowNum, colNum, fieldName));
//        }
//    }
//
//    // 判断一行是否全为空
//    private boolean isAllBlank(List<String> data) {
//        if (data == null || data.isEmpty()) {
//            return true;
//        }
//        for (String cell : data) {
//            if (StringUtils.isNotBlank(cell)) {
//                return false;
//            }
//        }
//        return true;
//    }
//
//    // 检测中间是否有空行（例如第1行有内容，第3行有内容，但第2行是空行）
//    @Override
//    public void doAfterAllAnalysed(AnalysisContext context) {
//        // 跳过标题行的检查
//        if (processedRows.size() > 1) {
//            Integer minRow = processedRows.stream().min(Integer::compare).orElse(1);
//            Integer maxRow = processedRows.stream().max(Integer::compare).orElse(1);
//
//            for (int i = minRow + 1; i < maxRow; i++) { // minRow+1 跳过标题行
//                if (!processedRows.contains(i)) {
//                    errorMessages.add(String.format("第%d行: 不允许有空行", i));
//                }
//            }
//        }
//    }
//
//    // 处理额外信息（可选）
//    @Override
//    public void extra(CellExtra extra, AnalysisContext context) {
//        // 可以在这里处理合并单元格等额外信息
//    }
//
//    // 获取校验结果
//    public boolean isValid() {
//        //打印错误信息
//        errorMessages.forEach(System.out::println);
//        return errorMessages.isEmpty();
//    }
//
//    // 获取错误信息
//    public List<String> getErrorMessages() {
//        return new ArrayList<>(errorMessages);
//    }
//
//}
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
    // 仅首个非空行作为标题行进行校验。
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

        // 检查标题行
        if (!headerChecked) {
            checkHeaders(rowData);
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
    private void checkHeaders(List<String> headers) {
        if (headers == null || headers.size() < 3) {
            addError("第1行: 标题行缺少必要的列，必须是：题目、答案、解析");
            return;
        }

        for (int i = 0; i < requiredHeaders.size(); i++) {
            String expected = requiredHeaders.get(i);
            String actual = i < headers.size() ? headers.get(i) : "";

            if (!expected.equals(actual)) {
                addError(String.format("第1行第%d列: 标题应该是'%s'，实际是'%s'",
                        i + 1, expected, actual));
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
