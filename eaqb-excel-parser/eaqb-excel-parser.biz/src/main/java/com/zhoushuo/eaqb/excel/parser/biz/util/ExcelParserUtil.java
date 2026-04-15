package com.zhoushuo.eaqb.excel.parser.biz.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.zhoushuo.eaqb.excel.parser.biz.listener.QuestionExcelListener;
import com.zhoushuo.eaqb.excel.parser.biz.model.dto.QuestionDataDTO;
import com.zhoushuo.framework.commono.exception.BizException;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
public class ExcelParserUtil {

    public static List<QuestionDataDTO> parseExcel(InputStream inputStream, int headRowNumber) {
        List<QuestionDataDTO> questions = new ArrayList<>();
        parseExcelInChunks(inputStream, headRowNumber, Integer.MAX_VALUE, questions::addAll);
        return questions;
    }

    public static void parseExcelInChunks(InputStream inputStream,
                                          int headRowNumber,
                                          int chunkSize,
                                          Consumer<List<QuestionDataDTO>> chunkConsumer) {
        try {
            ExcelReader excelReader = EasyExcel.read(
                    inputStream,
                    QuestionDataDTO.class,
                    new QuestionExcelListener(chunkSize, chunkConsumer)
            ).build();
            ReadSheet readSheet = EasyExcel.readSheet(0)
                    .headRowNumber(headRowNumber)
                    .build();
            excelReader.read(readSheet);
            excelReader.finish();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析Excel文件失败", e);
            throw new RuntimeException("Excel文件解析失败: " + e.getMessage(), e);
        }
    }
}