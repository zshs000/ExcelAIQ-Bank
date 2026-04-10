package com.zhoushuo.eaqb.question.bank.resp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommitImportBatchResponseDTO {
    private Long batchId;
    private String status;
    private Integer importedCount;
}
