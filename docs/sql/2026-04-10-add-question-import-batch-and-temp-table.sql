CREATE TABLE IF NOT EXISTS `t_question_import_batch` (
  `id` BIGINT NOT NULL COMMENT '导入批次ID',
  `file_id` BIGINT NOT NULL COMMENT '来源文件ID',
  `user_id` BIGINT NOT NULL COMMENT '发起导入的用户ID',
  `status` VARCHAR(32) NOT NULL COMMENT 'APPENDING/READY/COMMITTED/FAILED/ABORTED',
  `chunk_size` INT NOT NULL COMMENT '当前批次分块大小',
  `expected_chunk_count` INT DEFAULT NULL COMMENT 'finish 阶段确认的预期 chunk 数',
  `received_chunk_count` INT NOT NULL DEFAULT 0 COMMENT '已接收 chunk 数',
  `total_row_count` INT NOT NULL DEFAULT 0 COMMENT '已接收总题目数',
  `imported_count` INT DEFAULT NULL COMMENT '正式提交成功的题目数',
  `error_message` VARCHAR(512) DEFAULT NULL COMMENT '失败原因',
  `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_question_import_batch_user_status` (`user_id`, `status`),
  KEY `idx_question_import_batch_file` (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目导入批次主表';

CREATE TABLE IF NOT EXISTS `t_question_import_temp` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '临时明细主键',
  `batch_id` BIGINT NOT NULL COMMENT '所属导入批次ID',
  `chunk_no` INT NOT NULL COMMENT '第几个 chunk，从 1 开始',
  `row_no` INT NOT NULL COMMENT 'chunk 内行号，从 1 开始',
  `chunk_row_count` INT NOT NULL COMMENT '该 chunk 原始行数',
  `content_hash` VARCHAR(128) NOT NULL COMMENT '该 chunk 的内容指纹',
  `content` LONGTEXT NOT NULL COMMENT '题目内容',
  `answer` LONGTEXT DEFAULT NULL COMMENT '题目答案',
  `analysis` LONGTEXT DEFAULT NULL COMMENT '题目解析',
  `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_question_import_temp_batch_chunk_row` (`batch_id`, `chunk_no`, `row_no`),
  KEY `idx_question_import_temp_batch_chunk` (`batch_id`, `chunk_no`),
  CONSTRAINT `fk_question_import_temp_batch`
    FOREIGN KEY (`batch_id`) REFERENCES `t_question_import_batch` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目导入临时明细表';
