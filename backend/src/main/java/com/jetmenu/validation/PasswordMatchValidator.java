package com.jetmenu.validation;

import com.jetmenu.merchant.MerchantRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordMatchValidator implements ConstraintValidator<PasswordMatch, MerchantRequest> {

    @Override
    public boolean isValid(MerchantRequest value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        String password = value.getPassword();
        String confirmPassword = value.getConfirmPassword();

        if (password == null || password.isBlank() || confirmPassword == null || confirmPassword.isBlank()) {
            return true;
        }

        return password.equals(confirmPassword);
    }
}

