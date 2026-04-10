package com.zhoushuo.eaqb.excel.parser.biz.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.zhoushuo.eaqb.excel.parser.biz.model.dto.QuestionDataDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class QuestionExcelListener extends AnalysisEventListener<QuestionDataDTO> {

    private final int chunkSize;
    private final Consumer<List<QuestionDataDTO>> chunkConsumer;
    private final List<QuestionDataDTO> buffer = new ArrayList<>();

    public QuestionExcelListener(int chunkSize, Consumer<List<QuestionDataDTO>> chunkConsumer) {
        this.chunkSize = chunkSize;
        this.chunkConsumer = chunkConsumer;
    }

    @Override
    public void invoke(QuestionDataDTO question, AnalysisContext context) {
        buffer.add(question);
        if (buffer.size() >= chunkSize) {
            flush();
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        flush();
    }

    private void flush() {
        if (buffer.isEmpty()) {
            return;
        }
        chunkConsumer.accept(new ArrayList<>(buffer));
        buffer.clear();
    }
}
