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
public class QuestionImportTempDO {
    private Long id;
    private Long batchId;
    private Integer chunkNo;
    private Integer rowNo;
    private Integer chunkRowCount;
    private String contentHash;
    private String content;
    private String answer;
    private String analysis;
    private LocalDateTime createdTime;
}
