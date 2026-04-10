package com.zhoushuo.eaqb.question.bank.req;

import lombok.Data;

@Data
public class CreateImportBatchRequestDTO {
    private Long fileId;
    private Integer chunkSize;
}
