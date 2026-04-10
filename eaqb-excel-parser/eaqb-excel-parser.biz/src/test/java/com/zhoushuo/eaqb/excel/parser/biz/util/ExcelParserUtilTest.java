package com.zhoushuo.eaqb.excel.parser.biz.util;

import com.alibaba.excel.EasyExcel;
import com.zhoushuo.eaqb.excel.parser.biz.model.dto.QuestionDataDTO;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
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

    @Test
    void parseExcelInChunks_shouldFlushRowsByChunkSize() throws IOException {
        byte[] excelBytes = buildExcelBytes(List.of(
                List.of("题目一", "A", "解析一"),
                List.of("题目二", "B", "解析二"),
                List.of("题目三", "C", "解析三")
        ));
        List<List<QuestionDataDTO>> chunks = new ArrayList<>();

        ExcelParserUtil.parseExcelInChunks(new ByteArrayInputStream(excelBytes), 1, 2, chunk -> chunks.add(new ArrayList<>(chunk)));

        assertEquals(2, chunks.size());
        assertEquals(2, chunks.get(0).size());
        assertEquals(1, chunks.get(1).size());
        assertEquals("题目一", chunks.get(0).get(0).getQuestionContent());
        assertEquals("题目三", chunks.get(1).get(0).getQuestionContent());
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
