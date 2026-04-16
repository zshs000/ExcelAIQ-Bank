package com.zhoushuo.eaqb.question.bank.biz.service.impl.imports;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionImportTempDO;
import com.zhoushuo.eaqb.question.bank.biz.enums.QuestionProcessStatusEnum;
import com.zhoushuo.eaqb.question.bank.req.AppendImportChunkRequestDTO;
import com.zhoushuo.eaqb.question.bank.req.ImportQuestionRowDTO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 导入批次装配器。
 * 负责在“请求DTO <-> 持久化DO”之间做字段映射，避免业务编排层堆叠 builder 细节。
 */
public class ImportBatchAssembler {

    /**
     * 将 append 请求中的 rows 转成临时明细行。
     * 说明：
     * 1. 一个 chunk 会展开成多条 QuestionImportTempDO；
     * 2. chunk 级元信息（chunkRowCount/contentHash）会冗余写入该 chunk 的每一行；
     * 3. rowNo 按 chunk 内顺序从 1 开始。
     */
    public List<QuestionImportTempDO> toTempRows(AppendImportChunkRequestDTO request) {
        List<QuestionImportTempDO> rows = new ArrayList<>(request.getRows().size());
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < request.getRows().size(); i++) {
            ImportQuestionRowDTO row = request.getRows().get(i);
            // 将请求里的单行题目映射为临时表行，并带上批次/分块上下文。
            rows.add(QuestionImportTempDO.builder()
                    .batchId(request.getBatchId())
                    .chunkNo(request.getChunkNo())
                    .rowNo(i + 1)
                    .chunkRowCount(request.getRowCount())
                    .contentHash(request.getContentHash())
                    .content(row.getContent())
                    .answer(row.getAnswer())
                    .analysis(row.getAnalysis())
                    .createdTime(now)
                    .build());
        }
        return rows;
    }

    /**
     * 将临时明细行转成正式题目行（commit 阶段使用）。
     * 说明：
     * 1. questionIds 与 tempRows 按下标一一对应；
     * 2. 正式题目初始流程状态统一置为 WAITING；
     * 3. createdBy 继承批次 owner。
     */
    public List<QuestionDO> toQuestions(List<QuestionImportTempDO> tempRows, List<Long> questionIds, Long createdBy) {
        LocalDateTime now = LocalDateTime.now();
        List<QuestionDO> questions = new ArrayList<>(tempRows.size());
        for (int i = 0; i < tempRows.size(); i++) {
            QuestionImportTempDO tempRow = tempRows.get(i);
            // 将临时题目内容提升为正式题目，并分配对应的正式ID。
            questions.add(QuestionDO.builder()
                    .id(questionIds.get(i))
                    .content(tempRow.getContent())
                    .answer(tempRow.getAnswer())
                    .analysis(tempRow.getAnalysis())
                    .processStatus(QuestionProcessStatusEnum.WAITING.getCode())
                    .createdBy(createdBy)
                    .createdTime(now)
                    .updatedTime(now)
                    .build());
        }
        return questions;
    }
}
