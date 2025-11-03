package com.zhoushuo.eaqb.excel.parser.biz.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "easyexcel")
public class EasyExcelConfig {
    // 是否自动关闭流
    private boolean autoCloseStream = true;
    // 是否使用默认的类型转换
    private boolean useDefaultConverter = true;
    // 解析时的批处理大小
    private int batchSize = 1000;
    // 标题行号
    private int headRowNumber = 1;
}