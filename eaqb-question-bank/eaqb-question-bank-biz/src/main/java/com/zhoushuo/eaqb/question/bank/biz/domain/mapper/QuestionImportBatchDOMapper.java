package com.zhoushuo.eaqb.question.bank.biz.domain.mapper;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionImportBatchDO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface QuestionImportBatchDOMapper {
    int insertSelective(QuestionImportBatchDO record);

    QuestionImportBatchDO selectByPrimaryKey(Long id);

    int increaseAfterChunkAccepted(@Param("id") Long id,
                                   @Param("expectedStatus") String expectedStatus,
                                   @Param("chunkIncrement") Integer chunkIncrement,
                                   @Param("rowIncrement") Integer rowIncrement);

    int markFailed(@Param("id") Long id,
                   @Param("expectedStatus") String expectedStatus,
                   @Param("errorMessage") String errorMessage);

    int markReady(@Param("id") Long id,
                  @Param("expectedStatus") String expectedStatus,
                  @Param("expectedChunkCount") Integer expectedChunkCount,
                  @Param("expectedRowCount") Integer expectedRowCount);

    int markCommitted(@Param("id") Long id,
                      @Param("expectedStatus") String expectedStatus,
                      @Param("importedCount") Integer importedCount);

    int markAbortedByIds(@Param("ids") List<Long> ids,
                         @Param("expectedStatus") String expectedStatus,
                         @Param("errorMessage") String errorMessage);

    List<Long> selectExpiredBatchIdsByStatusAndUpdatedBefore(@Param("status") String status,
                                                             @Param("updatedBefore") LocalDateTime updatedBefore,
                                                             @Param("limit") Integer limit);

    int deleteByIds(@Param("ids") List<Long> ids);
}
