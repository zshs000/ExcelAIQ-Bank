package com.zhoushuo.eaqb.excel.parser.biz.domain.mapper;

import com.zhoushuo.eaqb.excel.parser.biz.domain.dataobject.ExcelPreUploadRecordDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Excel预上传记录Mapper
 */
@Mapper
public interface ExcelPreUploadRecordDOMapper {

    /**
     * 插入预上传记录
     * @param record 预上传记录
     * @return 影响行数
     */
    int insert(ExcelPreUploadRecordDO record);

    /**
     * 根据ID查询预上传记录
     * @param id 预上传记录ID
     * @return 预上传记录
     */
    ExcelPreUploadRecordDO selectById(Long id);
}