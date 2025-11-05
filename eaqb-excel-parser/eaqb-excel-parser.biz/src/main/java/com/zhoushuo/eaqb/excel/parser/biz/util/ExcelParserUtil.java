package com.zhoushuo.eaqb.excel.parser.biz.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.zhoushuo.eaqb.excel.parser.biz.listener.QuestionExcelListener;
import com.zhoushuo.eaqb.excel.parser.biz.model.dto.QuestionDataDTO;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.List;

@Slf4j
public class ExcelParserUtil {

    /**
     * 解析Excel文件流为题目列表
     * @param inputStream Excel文件输入流
     * @return 解析后的题目列表
     */
    public static List<QuestionDataDTO> parseExcel(InputStream inputStream) {
        try {
            QuestionExcelListener listener = new QuestionExcelListener();
            ExcelReader excelReader = EasyExcel.read(inputStream, QuestionDataDTO.class, listener).build();
            ReadSheet readSheet = EasyExcel.readSheet(0).build();
            excelReader.read(readSheet);
            excelReader.finish();

            return listener.getQuestions();
        } catch (Exception e) {
            log.error("解析Excel文件失败", e);
            throw new RuntimeException("Excel文件解析失败: " + e.getMessage(), e);
        }
    }
}