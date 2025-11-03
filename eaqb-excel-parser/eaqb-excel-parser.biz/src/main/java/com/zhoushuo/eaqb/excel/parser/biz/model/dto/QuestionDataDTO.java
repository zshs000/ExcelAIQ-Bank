package com.zhoushuo.eaqb.excel.parser.biz.model.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class QuestionDataDTO {
    @ExcelProperty(value = "题目内容", index = 0)
    private String questionContent;


    @ExcelProperty(value = "答案", index = 1)
    private String answer;



    //这个暂时不由ai生成，重点在答案
    @ExcelProperty(value = "解析", index = 2)
    private String explanation;
}