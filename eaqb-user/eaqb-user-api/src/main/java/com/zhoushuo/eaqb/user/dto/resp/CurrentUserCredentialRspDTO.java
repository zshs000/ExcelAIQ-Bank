package com.zhoushuo.eaqb.user.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CurrentUserCredentialRspDTO {

    private Long id;

    private String phone;
}
