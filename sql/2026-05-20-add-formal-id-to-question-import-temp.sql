ALTER TABLE `t_question_import_temp`
  ADD COLUMN `formal_id` BIGINT DEFAULT NULL COMMENT '正式题目ID' AFTER `id`;

CREATE INDEX `idx_question_import_temp_batch_formal_id`
  ON `t_question_import_temp` (`batch_id`, `formal_id`);
