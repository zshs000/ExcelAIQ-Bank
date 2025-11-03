package com.zhoushuo.eaqb.excel.parser.biz.domain.mapper;

import com.zhoushuo.eaqb.excel.parser.biz.domain.dataobject.FileInfoDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileInfoDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(FileInfoDO record);

    int insertSelective(FileInfoDO record);

    FileInfoDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(FileInfoDO record);

    int updateByPrimaryKey(FileInfoDO record);
}