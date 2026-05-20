package com.zhoushuo.eaqb.question.bank.biz.domain.mapper;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionImportTempDO;
import com.zhoushuo.eaqb.question.bank.biz.domain.model.QuestionImportFormalIdBinding;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface QuestionImportTempDOMapper {
    int batchInsert(@Param("list") List<QuestionImportTempDO> rows);

    QuestionImportTempDO selectChunkMeta(@Param("batchId") Long batchId, @Param("chunkNo") Integer chunkNo);

    List<QuestionImportTempDO> selectByBatchIdOrderByChunkNoAndRowNo(@Param("batchId") Long batchId);

    int countByBatchId(@Param("batchId") Long batchId);

    int countUnboundFormalId(@Param("batchId") Long batchId);

    int countDistinctFormalId(@Param("batchId") Long batchId);

    List<Long> selectUnboundIdsByBatchId(@Param("batchId") Long batchId, @Param("limit") int limit);

    int bindFormalIds(@Param("batchId") Long batchId,
                      @Param("bindings") List<QuestionImportFormalIdBinding> bindings);

    int deleteByBatchIds(@Param("batchIds") List<Long> batchIds);
}
