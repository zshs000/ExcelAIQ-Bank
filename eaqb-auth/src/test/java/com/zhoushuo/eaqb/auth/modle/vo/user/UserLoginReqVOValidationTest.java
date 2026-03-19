package com.zhoushuo.eaqb.auth.modle.vo.user;

import com.zhoushuo.eaqb.auth.enums.LoginTypeEnum;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserLoginReqVOValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validate_passwordLoginWithoutPassword_shouldFail() {
        UserLoginReqVO request = UserLoginReqVO.builder()
                .phone("13800138000")
                .type(LoginTypeEnum.PASSWORD.getValue())
                .build();

        Set<ConstraintViolation<UserLoginReqVO>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        ConstraintViolation<UserLoginReqVO> violation = violations.iterator().next();
        assertEquals("password", violation.getPropertyPath().toString());
        assertEquals("密码不能为空", violation.getMessage());
    }

    @Test
    void validate_verificationCodeLoginWithoutCode_shouldFail() {
        UserLoginReqVO request = UserLoginReqVO.builder()
                .phone("13800138000")
                .type(LoginTypeEnum.VERIFICATION_CODE.getValue())
                .build();

        Set<ConstraintViolation<UserLoginReqVO>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        ConstraintViolation<UserLoginReqVO> violation = violations.iterator().next();
        assertEquals("code", violation.getPropertyPath().toString());
        assertEquals("验证码不能为空", violation.getMessage());
    }

    @Test
    void validate_passwordLoginWithPassword_shouldPass() {
        UserLoginReqVO request = UserLoginReqVO.builder()
                .phone("13800138000")
                .password("123456")
                .type(LoginTypeEnum.PASSWORD.getValue())
                .build();

        Set<ConstraintViolation<UserLoginReqVO>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }
}
