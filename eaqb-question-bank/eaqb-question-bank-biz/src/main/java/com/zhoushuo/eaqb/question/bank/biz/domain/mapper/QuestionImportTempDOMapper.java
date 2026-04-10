package com.zhoushuo.eaqb.question.bank.biz.domain.mapper;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionImportTempDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface QuestionImportTempDOMapper {
    int batchInsert(@Param("list") List<QuestionImportTempDO> rows);

    QuestionImportTempDO selectChunkMeta(@Param("batchId") Long batchId, @Param("chunkNo") Integer chunkNo);

    List<QuestionImportTempDO> selectByBatchIdOrderByChunkNoAndRowNo(@Param("batchId") Long batchId);

    int deleteByBatchIds(@Param("batchIds") List<Long> batchIds);
}
