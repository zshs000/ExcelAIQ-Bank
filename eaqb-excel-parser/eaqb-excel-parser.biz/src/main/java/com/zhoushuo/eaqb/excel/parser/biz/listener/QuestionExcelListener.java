package com.zhoushuo.eaqb.excel.parser.biz.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;

import com.zhoushuo.eaqb.excel.parser.biz.model.dto.QuestionDataDTO;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class QuestionExcelListener extends AnalysisEventListener<QuestionDataDTO> {

    private final List<QuestionDataDTO> questions = new ArrayList<>();

    @Override
    public void invoke(QuestionDataDTO question, AnalysisContext context) {
        questions.add(question);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // 可以在这里做一些最终处理
    }
}