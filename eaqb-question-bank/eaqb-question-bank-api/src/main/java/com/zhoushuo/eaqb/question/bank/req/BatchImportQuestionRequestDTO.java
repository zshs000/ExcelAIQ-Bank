package com.zhoushuo.eaqb.question.bank.req;

import lombok.Data;

import java.util.List;
@Data
public class BatchImportQuestionRequestDTO {
    // 批量导入的题目列表
    private List<QuestionDTO> questions;
}
