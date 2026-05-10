package com.zhoushuo.eaqb.user.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CurrentUserProfileRspDTO {

    private Long id;

    private String phone;

    private String eaqbId;

    private String nickname;

    private Integer sex;

    private LocalDate birthday;

    private String introduction;

    private String avatarUrl;

    private String backgroundImgUrl;
}
