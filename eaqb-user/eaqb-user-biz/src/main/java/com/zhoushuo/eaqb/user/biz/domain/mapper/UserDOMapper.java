package com.zhoushuo.eaqb.user.biz.domain.mapper;

import com.zhoushuo.eaqb.user.biz.domain.dataobject.UserDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDateTime;

public interface UserDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(UserDO record);

    int insertSelective(UserDO record);

    UserDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(UserDO record);

    int updatePasswordByIdIfActive(@Param("id") Long id,
                                   @Param("password") String password,
                                   @Param("updateTime") LocalDateTime updateTime);

    int updateByPrimaryKey(UserDO record);

    UserDO selectByPhone(String phone);

    List<UserDO> selectAdminUserList();
}
