package com.zhoushuo.eaqb.question.bank.biz.controller;

import com.zhoushuo.eaqb.question.bank.biz.model.vo.FailedOutboxEventVO;
import com.zhoushuo.eaqb.question.bank.biz.service.impl.QuestionOutboxAdminAppService;
import com.zhoushuo.framework.biz.operationlog.aspect.ApiOperationLog;
import com.zhoushuo.framework.commono.response.Response;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/question/admin")
@Validated
public class QuestionAdminController {

    private final QuestionOutboxAdminAppService questionOutboxAdminAppService;

    public QuestionAdminController(QuestionOutboxAdminAppService questionOutboxAdminAppService) {
        this.questionOutboxAdminAppService = questionOutboxAdminAppService;
    }

    @GetMapping("/outbox/failed")
    @ApiOperationLog(description = "管理员查看失败 outbox 列表")
    public Response<List<FailedOutboxEventVO>> listFailedOutboxEvents() {
        return questionOutboxAdminAppService.listFailedOutboxEvents();
    }

    @PostMapping("/outbox/{eventId}/retry")
    @ApiOperationLog(description = "管理员人工重试失败 outbox")
    public Response<Void> retryFailedOutboxEvent(@PathVariable("eventId") @NotNull Long eventId) {
        return questionOutboxAdminAppService.retryFailedOutboxEvent(eventId);
    }
}
