package com.zhoushuo.eaqb.question.bank.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionImportBatchDO {
    private Long id;
    private Long fileId;
    private Long userId;
    private String status;
    private Integer chunkSize;
    private Integer expectedChunkCount;
    private Integer receivedChunkCount;
    private Integer totalRowCount;
    private Integer importedCount;
    private String errorMessage;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
