package com.zhoushuo.eaqb.question.bank.req;

import lombok.Data;

import java.util.List;

@Data
public class AppendImportChunkRequestDTO {
    private Long batchId;
    private Integer chunkNo;
    private Integer rowCount;
    private String hashVersion;
    private String contentHash;
    private List<ImportQuestionRowDTO> rows;
}
