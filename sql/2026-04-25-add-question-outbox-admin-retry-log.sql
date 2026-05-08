create table question_outbox_admin_retry_log
(
    id            bigint       not null comment '主键ID',
    event_id      bigint       null comment 'outbox事件ID',
    task_id       bigint       null comment '流程任务ID',
    question_id   bigint       null comment '题目ID',
    admin_user_id bigint       null comment '执行人工重试的管理员ID',
    error_message text         not null comment '人工重试失败原因',
    created_time  datetime     not null comment '创建时间',
    updated_time  datetime     not null comment '更新时间',
    primary key (id)
) comment '题目outbox管理员人工重试失败日志表';

create index idx_qoarl_event_created
    on question_outbox_admin_retry_log (event_id, created_time);

create index idx_qoarl_admin_created
    on question_outbox_admin_retry_log (admin_user_id, created_time);
