package com.zhoushuo.eaqb.auth.validator;

import com.zhoushuo.eaqb.auth.enums.LoginTypeEnum;
import com.zhoushuo.eaqb.auth.model.vo.user.UserLoginReqVO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.apache.commons.lang3.StringUtils;

public class LoginRequestValidator implements ConstraintValidator<LoginRequestValid, UserLoginReqVO> {

    @Override
    public boolean isValid(UserLoginReqVO value, ConstraintValidatorContext context) {
        if (value == null || value.getType() == null) {
            return true;
        }

        LoginTypeEnum loginType = LoginTypeEnum.valueOf(value.getType());
        if (loginType == null) {
            return true;
        }

        if (loginType == LoginTypeEnum.PASSWORD && StringUtils.isBlank(value.getPassword())) {
            addViolation(context, "password", "密码不能为空");
            return false;
        }

        if (loginType == LoginTypeEnum.VERIFICATION_CODE && StringUtils.isBlank(value.getCode())) {
            addViolation(context, "code", "验证码不能为空");
            return false;
        }

        return true;
    }

    private void addViolation(ConstraintValidatorContext context, String fieldName, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(fieldName)
                .addConstraintViolation();
    }
}
