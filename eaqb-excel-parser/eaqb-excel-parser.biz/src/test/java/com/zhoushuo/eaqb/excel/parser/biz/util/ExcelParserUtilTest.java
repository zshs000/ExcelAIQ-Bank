package com.zhoushuo.eaqb.excel.parser.biz.util;

import com.alibaba.excel.EasyExcel;
import com.zhoushuo.eaqb.excel.parser.biz.model.dto.QuestionDataDTO;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcelParserUtilTest {

    @Test
    void parseExcel_shouldSkipHeaderRowWhenHeadRowNumberIsOne() throws IOException {
        byte[] excelBytes = buildExcelBytes(List.of(List.of("题目一", "A", "解析一")));

        List<QuestionDataDTO> questions = ExcelParserUtil.parseExcel(new ByteArrayInputStream(excelBytes), 1);

        assertEquals(1, questions.size());
        assertEquals("题目一", questions.get(0).getQuestionContent());
        assertEquals("A", questions.get(0).getAnswer());
        assertEquals("解析一", questions.get(0).getExplanation());
    }

    private static byte[] buildExcelBytes(List<List<String>> rows) throws IOException {
        List<List<String>> head = Arrays.asList(
                List.of("题目"),
                List.of("答案"),
                List.of("解析")
        );
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out).head(head).sheet("Sheet1").doWrite(rows);
            return out.toByteArray();
        }
    }
}
