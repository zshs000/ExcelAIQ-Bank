package com.zhoushuo.eaqb.auth.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = LoginRequestValidator.class)
public @interface LoginRequestValid {

    String message() default "登录请求参数不合法";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
