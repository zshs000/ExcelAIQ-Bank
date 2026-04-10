package com.zhoushuo.eaqb.question.bank.resp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateImportBatchResponseDTO {
    private Long batchId;
    private String status;
}
