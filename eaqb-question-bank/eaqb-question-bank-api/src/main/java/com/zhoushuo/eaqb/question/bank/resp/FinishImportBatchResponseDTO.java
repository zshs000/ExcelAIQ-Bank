package com.zhoushuo.eaqb.question.bank.resp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FinishImportBatchResponseDTO {
    private Long batchId;
    private String status;
    private Integer expectedChunkCount;
    private Integer totalRowCount;
}
