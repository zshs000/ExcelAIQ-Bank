package com.zhoushuo.eaqb.question.bank.biz.domain.mapper;

import com.zhoushuo.eaqb.question.bank.biz.domain.dataobject.QuestionDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface QuestionDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(QuestionDO record);

    int insertSelective(QuestionDO record);

    QuestionDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(QuestionDO record);

    int updateByPrimaryKeyWithBLOBs(QuestionDO record);

    int updateByPrimaryKey(QuestionDO record);


    /**
     * 批量插入题目
     * @param list 题目列表
     * @return 插入的行数
     */
    int batchInsert(@Param("list") List<QuestionDO> list);

    /**
     * 分页条件查询
     * @param questionDO
     * @return
     */
    List<QuestionDO> selectByExample(QuestionDO questionDO);

    /**
     * 批量删除
     * @param authorizedIds
     * @return
     */
    int deleteBatch(List<Long> authorizedIds);

    /**
     * 仅删除当前用户且仍处于允许编辑状态的题目。
     */
    int deleteBatchByCreatorAndStatuses(@Param("ids") List<Long> ids,
                                        @Param("createdBy") Long createdBy,
                                        @Param("statuses") List<String> statuses);

    /**
     * 批量更新题目状态
     * @param ids 题目ID列表
     * @param status 新状态
     * @return 更新的行数
     */
    int updateBatchStatus(@Param("ids") List<Long> ids, @Param("status") String status);

    /**
     * 更新题目状态并清空答案（answer = null）
     * @param id 题目ID
     * @param status 新状态
     * @return 更新行数
     */
    int updateStatusAndClearAnswer(@Param("id") Long id, @Param("status") String status);

    /**
     * 按“预期当前状态 -> 目标状态”做单题 CAS 流转。
     */
    int transitStatus(@Param("id") Long id,
                      @Param("expectedStatus") String expectedStatus,
                      @Param("targetStatus") String targetStatus);

    /**
     * 在 CAS 流转的同时覆盖答案。
     */
    int transitStatusAndAnswer(@Param("id") Long id,
                               @Param("expectedStatus") String expectedStatus,
                               @Param("targetStatus") String targetStatus,
                               @Param("answer") String answer);

    /**
     * 在 CAS 流转的同时清空答案。
     */
    int transitStatusAndClearAnswerByExpectedStatus(@Param("id") Long id,
                                                    @Param("expectedStatus") String expectedStatus,
                                                    @Param("targetStatus") String targetStatus);

    /**
     * 根据ID列表批量查询题目
     * @param ids 题目ID列表
     * @return 题目列表
     */
    List<QuestionDO> selectBatchByIds(List<Long> ids);

    /**
     * 仅在创建人和状态都未变化时更新题目内容。
     */
    int updateEditableQuestion(@Param("question") QuestionDO question,
                               @Param("createdBy") Long createdBy,
                               @Param("expectedStatus") String expectedStatus);
}
