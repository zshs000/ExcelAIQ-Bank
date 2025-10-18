package com.zhoushuo.eaqb.auth.modle.vo.verificationcode;

import com.zhoushuo.framework.commono.validator.PhoneNumber;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SendVerificationCodeReqVO {
    @PhoneNumber
    @NotBlank(message = "手机号不能为空")
    private String phone;

}