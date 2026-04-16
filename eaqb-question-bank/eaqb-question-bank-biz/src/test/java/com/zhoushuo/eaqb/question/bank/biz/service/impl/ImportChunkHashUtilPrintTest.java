package com.zhoushuo.eaqb.question.bank.biz.service.impl;

import com.zhoushuo.eaqb.question.bank.req.ImportQuestionRowDTO;
import com.zhoushuo.eaqb.question.bank.util.ImportChunkHashUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ImportChunkHashUtilPrintTest {

    @Test
    void print_updateField_effect_and_v1_v2_diff() {
        List<ImportQuestionRowDTO> rowsA = List.of(buildRow("x", "y\nz", ""));
        List<ImportQuestionRowDTO> rowsB = List.of(buildRow("x\ny", "z", ""));

        System.out.println("===== 用例A =====");
        System.out.println("原始字段: content='x', answer='y\\nz', analysis=''");
        printV1Text(rowsA, "A");
        System.out.println("v2拼接可读串(A): \"" + buildV2ReadableText(rowsA) + "\"");
        byte[] v2InputA = buildV2InputAndPrint(rowsA, "A");
        String v2HashA = ImportChunkHashUtil.computeHash(ImportChunkHashUtil.HASH_VERSION_V2, rowsA);
        System.out.println("v2输入字节流(hex, A): " + toHex(v2InputA));
        System.out.println("v2哈希(A): " + v2HashA);

        System.out.println();
        System.out.println("===== 用例B =====");
        System.out.println("原始字段: content='x\\ny', answer='z', analysis=''");
        printV1Text(rowsB, "B");
        System.out.println("v2拼接可读串(B): \"" + buildV2ReadableText(rowsB) + "\"");
        byte[] v2InputB = buildV2InputAndPrint(rowsB, "B");
        String v2HashB = ImportChunkHashUtil.computeHash(ImportChunkHashUtil.HASH_VERSION_V2, rowsB);
        System.out.println("v2输入字节流(hex, B): " + toHex(v2InputB));
        System.out.println("v2哈希(B): " + v2HashB);

        byte[] v1InputA = buildV1Input(rowsA);
        byte[] v1InputB = buildV1Input(rowsB);
        String v1HashA = sha256Hex(v1InputA);
        String v1HashB = sha256Hex(v1InputB);

        System.out.println();
        System.out.println("===== 最终对比结论 =====");
        System.out.println("v1输入字节流是否相同: " + Arrays.equals(v1InputA, v1InputB));
        System.out.println("v1哈希(A): " + v1HashA);
        System.out.println("v1哈希(B): " + v1HashB);
        System.out.println("v2输入字节流是否相同: " + Arrays.equals(v2InputA, v2InputB));
        System.out.println("v2哈希(A): " + v2HashA);
        System.out.println("v2哈希(B): " + v2HashB);

        assertEquals(v1HashA, v1HashB);
        assertNotEquals(v2HashA, v2HashB);
    }

    private void printV1Text(List<ImportQuestionRowDTO> rows, String tag) {
        String v1Text = buildV1JoinedText(rows);
        System.out.println("v1拼接文本(" + tag + "): \"" + printable(v1Text) + "\"");
    }

    private byte[] buildV2InputAndPrint(List<ImportQuestionRowDTO> rows, String tag) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < rows.size(); i++) {
            ImportQuestionRowDTO row = rows.get(i);
            System.out.println("[" + tag + "] 行#" + (i + 1));
            appendFieldV2(out, "题干(content)", row == null ? null : row.getContent());
            appendFieldV2(out, "答案(answer)", row == null ? null : row.getAnswer());
            appendFieldV2(out, "解析(analysis)", row == null ? null : row.getAnalysis());
        }
        return out.toByteArray();
    }

    private void appendFieldV2(ByteArrayOutputStream out, String fieldName, String value) {
        byte[] valueBytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        byte[] lenBytes = ByteBuffer.allocate(4).putInt(valueBytes.length).array();
        out.write(lenBytes, 0, lenBytes.length);
        out.write(valueBytes, 0, valueBytes.length);

        System.out.println("  字段=" + fieldName
                + " | 长度=" + valueBytes.length
                + " | 长度前缀hex=" + toHex(lenBytes)
                + " | 值hex=" + toHex(valueBytes)
                + " | 值=\"" + printable(value) + "\"");
    }

    private String buildV1JoinedText(List<ImportQuestionRowDTO> rows) {
        StringBuilder builder = new StringBuilder();
        for (ImportQuestionRowDTO row : rows) {
            appendV1Field(builder, row == null ? null : row.getContent());
            appendV1Field(builder, row == null ? null : row.getAnswer());
            appendV1Field(builder, row == null ? null : row.getAnalysis());
        }
        return builder.toString();
    }

    private byte[] buildV1Input(List<ImportQuestionRowDTO> rows) {
        return buildV1JoinedText(rows).getBytes(StandardCharsets.UTF_8);
    }

    private void appendV1Field(StringBuilder builder, String value) {
        if (value != null) {
            builder.append(value);
        }
        builder.append('\n');
    }

    private String buildV2ReadableText(List<ImportQuestionRowDTO> rows) {
        StringBuilder builder = new StringBuilder();
        for (ImportQuestionRowDTO row : rows) {
            appendV2FieldReadable(builder, row == null ? null : row.getContent());
            appendV2FieldReadable(builder, row == null ? null : row.getAnswer());
            appendV2FieldReadable(builder, row == null ? null : row.getAnalysis());
        }
        return builder.toString();
    }

    private void appendV2FieldReadable(StringBuilder builder, String value) {
        String text = value == null ? "" : value;
        int length = text.getBytes(StandardCharsets.UTF_8).length;
        builder.append('[').append(length).append(']').append(printable(text));
    }

    private String sha256Hex(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(input);
            return toHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ImportQuestionRowDTO buildRow(String content, String answer, String analysis) {
        ImportQuestionRowDTO row = new ImportQuestionRowDTO();
        row.setContent(content);
        row.setAnswer(answer);
        row.setAnalysis(analysis);
        return row;
    }

    private String printable(String value) {
        if (value == null) {
            return "null";
        }
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = b & 0xFF;
            if (v < 16) {
                builder.append('0');
            }
            builder.append(Integer.toHexString(v));
        }
        return builder.toString();
    }
}
