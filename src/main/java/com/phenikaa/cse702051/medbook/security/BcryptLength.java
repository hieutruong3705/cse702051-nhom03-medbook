package com.phenikaa.cse702051.medbook.security;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BcryptLengthValidator.class)
public @interface BcryptLength {
    String message() default "Mật khẩu không được vượt quá 72 byte UTF-8";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
