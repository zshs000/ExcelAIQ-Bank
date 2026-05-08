ALTER TABLE question_process_task
    ADD COLUMN source_question_status VARCHAR(32) NULL COMMENT 'Question status before this task moved into DISPATCHING';
