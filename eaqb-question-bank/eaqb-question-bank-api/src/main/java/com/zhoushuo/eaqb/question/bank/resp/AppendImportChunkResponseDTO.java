package com.zhoushuo.eaqb.question.bank.resp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AppendImportChunkResponseDTO {
    private Long batchId;
    private Integer chunkNo;
    private boolean duplicateChunk;
    private Integer receivedChunkCount;
    private Integer totalRowCount;
}
