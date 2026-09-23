package com.example.payments.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Currency;
import java.util.Set;
import java.util.stream.Collectors;

public class CurrencyValidator implements ConstraintValidator<ValidCurrency, String> {

    private static final Set<String> VALID_CURRENCIES = Currency.getAvailableCurrencies()
            .stream()
            .map(Currency::getCurrencyCode)
            .collect(Collectors.toUnmodifiableSet());

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        return VALID_CURRENCIES.contains(value.trim().toUpperCase());
    }
}
