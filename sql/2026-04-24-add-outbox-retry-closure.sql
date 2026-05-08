alter table question_outbox_event
    add column next_retry_time datetime null comment '下一次允许重试时间' after dispatch_retry_count,
    add column last_error text null comment '最后一次派发失败原因' after next_retry_time,
    add column last_error_time datetime null comment '最后一次派发失败时间' after last_error;

create index idx_question_outbox_event_status_retry_time
    on question_outbox_event (event_status, next_retry_time, created_time);
