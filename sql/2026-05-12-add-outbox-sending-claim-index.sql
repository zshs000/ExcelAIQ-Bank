create index idx_question_outbox_event_status_updated_time
    on question_outbox_event (event_status, updated_time);
