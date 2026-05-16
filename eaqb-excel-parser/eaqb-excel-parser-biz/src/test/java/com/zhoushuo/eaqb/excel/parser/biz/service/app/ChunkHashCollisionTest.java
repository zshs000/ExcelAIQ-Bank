package com.zhoushuo.eaqb.excel.parser.biz.service.app;

import com.zhoushuo.eaqb.question.bank.req.ImportQuestionRowDTO;
import com.zhoushuo.eaqb.question.bank.util.ImportChunkHashUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ChunkHashCollisionTest {

    @Test
    void computeHashV2_shouldNotCollideForDifferentFieldBoundaries() {
        // 两组数据只有字段边界不同，旧算法会误碰撞；v2 需要区分开。
        List<ImportQuestionRowDTO> rowsA = List.of(buildRow("x", "y\nz", ""));
        List<ImportQuestionRowDTO> rowsB = List.of(buildRow("x\ny", "z", ""));

        String hashA = ImportChunkHashUtil.computeHash(ImportChunkHashUtil.HASH_VERSION_V2, rowsA);
        String hashB = ImportChunkHashUtil.computeHash(ImportChunkHashUtil.HASH_VERSION_V2, rowsB);

        assertNotEquals(hashA, hashB);
    }

    @Test
    void duplicateRuleWithHashVersion_shouldNotMisjudgeAsDuplicate() {
        List<ImportQuestionRowDTO> rowsA = List.of(buildRow("x", "y\nz", ""));
        List<ImportQuestionRowDTO> rowsB = List.of(buildRow("x\ny", "z", ""));

        String existingHash = ImportChunkHashUtil.computeHash(ImportChunkHashUtil.HASH_VERSION_V2, rowsA);
        String retryHash = ImportChunkHashUtil.computeHash(ImportChunkHashUtil.HASH_VERSION_V2, rowsB);

        boolean duplicateByRule = 1 == 1
                && Objects.equals(ImportChunkHashUtil.HASH_VERSION_V2, ImportChunkHashUtil.HASH_VERSION_V2)
                && Objects.equals(existingHash, retryHash);

        assertFalse(duplicateByRule);
    }

    private ImportQuestionRowDTO buildRow(String content, String answer, String analysis) {
        ImportQuestionRowDTO row = new ImportQuestionRowDTO();
        row.setContent(content);
        row.setAnswer(answer);
        row.setAnalysis(analysis);
        return row;
    }
}
