ALTER TABLE t_file_info
    CHANGE COLUMN oss_url object_key VARCHAR(255) NULL COMMENT '对象存储中的完整对象路径，如 excel/123/9001.xlsx';
