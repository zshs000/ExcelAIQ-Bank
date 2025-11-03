package com.zhoushuo.eaqb.excel.parser.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileInfoDO {
    private Long id;

    private Long userId;

    private String fileName;

    private Long fileSize;

    private String ossUrl;

    private LocalDateTime uploadTime;
    //默认为UPLOADED'文件状态(UPLOADED/PARSED/ERROR)'- UPLOADED ：文件已成功上传到OSS并保存元数据，但尚未开始解析
    //- PARSING ：文件正在进行解析处理中（可能是耗时操作）
    //- PARSED ：文件已成功解析完成，可以使用解析结果
    //- FAILED ：文件解析失败，需要用户重新上传或修复文件
    private String status;
}