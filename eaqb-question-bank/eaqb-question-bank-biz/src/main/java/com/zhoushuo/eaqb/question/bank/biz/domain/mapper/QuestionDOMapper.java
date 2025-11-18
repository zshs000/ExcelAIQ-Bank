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
     * 批量更新题目状态
     * @param ids 题目ID列表
     * @param status 新状态
     * @return 更新的行数
     */
    int updateBatchStatus(@Param("ids") List<Long> ids, @Param("status") String status);

    /**
     * 根据ID列表批量查询题目
     * @param ids 题目ID列表
     * @return 题目列表
     */
    List<QuestionDO> selectBatchByIds(List<Long> ids);
}