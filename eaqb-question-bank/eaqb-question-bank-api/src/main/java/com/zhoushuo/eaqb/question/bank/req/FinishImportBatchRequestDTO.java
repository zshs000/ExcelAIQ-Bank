package com.zhoushuo.eaqb.question.bank.req;

import lombok.Data;

@Data
public class FinishImportBatchRequestDTO {
    private Long batchId;
    private Integer expectedChunkCount;
    private Integer expectedRowCount;
}
