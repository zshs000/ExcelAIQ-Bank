package com.zhoushuo.eaqb.question.bank.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FindImportBatchByFileResponseDTO {
    private boolean found;
    private Long batchId;
    private String status;
    private Integer expectedChunkCount;
    private Integer receivedChunkCount;
    private Integer totalRowCount;
    private Integer importedCount;
}
