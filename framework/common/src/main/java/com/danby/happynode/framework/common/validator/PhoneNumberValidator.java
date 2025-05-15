package com.danby.happynode.framework.common.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Collection;
import java.util.Collections;


public class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {

    @Override
    public void initialize(PhoneNumber constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    @Override
    public boolean isValid(String s, ConstraintValidatorContext constraintValidatorContext) {
        // 判断字符串是否为空或null
            if (s == null || s.isEmpty()) {
            // 如果为空或null，则返回true
            return true;
        }
        // 判断字符串是否匹配正则表达式
        return s.matches("^\\d{11}$");
    }

}
