package com.zhoushuo.eaqb.excel.parser.biz.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.zhoushuo.eaqb.excel.parser.biz.listener.QuestionExcelListener;
import com.zhoushuo.eaqb.excel.parser.biz.model.dto.QuestionDataDTO;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.List;

/**
 * Excel 解析工具类
 * 
 * 使用 EasyExcel 进行流式读取，避免大文件内存溢出
 * 采用监听器模式，边读边处理，内存占用小
 */
@Slf4j
public class ExcelParserUtil {

    /**
     * 解析Excel文件流为题目列表
     * 
     * 处理流程：
     * 1. 创建监听器用于收集解析数据
     * 2. 创建 EasyExcel 读取器，指定目标类型和监听器
     * 3. 读取第一个工作表（sheet 0）
     * 4. 流式读取，每读到一行就回调监听器
     * 5. 完成读取并释放资源
     * 6. 从监听器获取所有解析好的题目
     * 
     * 优势：
     * - 流式处理：不会一次性加载整个文件到内存
     * - 自动映射：根据 @ExcelProperty 注解自动映射列到字段
     * - 灵活性：支持从文件、网络、内存等任意 InputStream 读取
     * 
     * @param inputStream Excel文件输入流（支持 .xlsx 格式）
     * @param headRowNumber 标题行数量。当前模板固定为 1，显式传入避免依赖框架默认值。
     * @return 解析后的题目列表
     * @throws RuntimeException 解析失败时抛出，包含原始异常信息
     */
    public static List<QuestionDataDTO> parseExcel(InputStream inputStream, int headRowNumber) {
        try {
            // 1. 创建监听器（用于收集每一行解析出来的数据）
            QuestionExcelListener listener = new QuestionExcelListener();
            
            // 2. 创建 EasyExcel 读取器
            //    - inputStream: 数据源
            //    - QuestionDataDTO.class: 目标类型（根据 @ExcelProperty 注解自动映射）
            //    - listener: 监听器（每读到一行就回调 listener.invoke()）
            ExcelReader excelReader = EasyExcel.read(inputStream, QuestionDataDTO.class, listener).build();
            
            // 3. 指定读取第一个工作表（Excel 可以有多个 sheet，这里只读第一个）
            ReadSheet readSheet = EasyExcel.readSheet(0)
                    .headRowNumber(headRowNumber)
                    .build();
            
            // 4. 开始流式读取（边读边回调监听器，不会一次性加载整个文件）
            excelReader.read(readSheet);
            
            // 5. 完成读取，释放资源
            excelReader.finish();

            // 6. 从监听器获取所有解析好的题目
            return listener.getQuestions();
        } catch (Exception e) {
            log.error("解析Excel文件失败", e);
            throw new RuntimeException("Excel文件解析失败: " + e.getMessage(), e);
        }
    }
}
