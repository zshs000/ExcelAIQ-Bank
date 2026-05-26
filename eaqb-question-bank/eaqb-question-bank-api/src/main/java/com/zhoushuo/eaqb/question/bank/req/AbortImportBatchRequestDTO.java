package com.zhoushuo.eaqb.question.bank.req;

import lombok.Data;

@Data
public class AbortImportBatchRequestDTO {
    private Long batchId;
    private String reason;
}
