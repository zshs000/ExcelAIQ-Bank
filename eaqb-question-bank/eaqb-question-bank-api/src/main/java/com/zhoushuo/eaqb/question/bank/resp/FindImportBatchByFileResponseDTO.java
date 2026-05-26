package com.zhoushuo.eaqb.question.bank.resp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FindImportBatchByFileResponseDTO {
    private boolean found;
    private Long batchId;
    private String status;
    private Integer totalRowCount;
    private Integer importedCount;
}
