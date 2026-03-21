package com.zhoushuo.eaqb.user.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminUserListRspDTO {

    private Long id;

    private String eaqbId;

    private String nickname;

    private String phone;

    private Integer status;

    private LocalDateTime createTime;
}
