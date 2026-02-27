package com.zhoushuo.eaqb.question.bank.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
@Data
public class BatchImportQuestionRequestDTO {
    // 批量导入的题目列表
    @NotEmpty(message = "导入题目列表不能为空")
    @Valid
    private List<QuestionDTO> questions;
}
