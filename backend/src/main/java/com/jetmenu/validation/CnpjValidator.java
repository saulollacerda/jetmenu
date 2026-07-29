package com.jetmenu.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CnpjValidator implements ConstraintValidator<ValidCnpj, String> {

    private static final int CNPJ_LENGTH = 14;
    private static final int[] WEIGHTS_FIRST = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] WEIGHTS_SECOND = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }

        String digits = value.replaceAll("\\D", "");
        if (digits.length() != CNPJ_LENGTH) {
            return false;
        }

        if (allDigitsEqual(digits)) {
            return false;
        }

        int firstCheckDigit = calculateCheckDigit(digits.substring(0, 12), WEIGHTS_FIRST);
        int secondCheckDigit = calculateCheckDigit(digits.substring(0, 12) + firstCheckDigit, WEIGHTS_SECOND);

        return digits.charAt(12) == (char) ('0' + firstCheckDigit)
                && digits.charAt(13) == (char) ('0' + secondCheckDigit);
    }

    private boolean allDigitsEqual(String digits) {
        char first = digits.charAt(0);
        for (int i = 1; i < digits.length(); i++) {
            if (digits.charAt(i) != first) {
                return false;
            }
        }
        return true;
    }

    private int calculateCheckDigit(String value, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += Character.getNumericValue(value.charAt(i)) * weights[i];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}

