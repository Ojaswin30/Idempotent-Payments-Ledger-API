package com.example.payments.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyValidatorTest {

    private CurrencyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CurrencyValidator();
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "EUR", "INR", "GBP", "JPY", "CAD", "AUD", "CHF", "SGD"})
    @DisplayName("Should accept valid ISO 4217 currency codes")
    void shouldAcceptValidCurrencies(String currency) {
        assertThat(validator.isValid(currency, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"inr", "usd", "eur"})
    @DisplayName("Should accept lowercase valid currencies after trimming and uppercase conversion")
    void shouldAcceptLowercaseValidCurrencies(String currency) {
        assertThat(validator.isValid(currency, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "XYZ", "US", "USDT", "BITCOIN", "123", "EURO"})
    @DisplayName("Should reject invalid or unsupported currency codes")
    void shouldRejectInvalidCurrencies(String currency) {
        assertThat(validator.isValid(currency, null)).isFalse();
    }

    @Test
    @DisplayName("Should reject null currency")
    void shouldRejectNullCurrency() {
        assertThat(validator.isValid(null, null)).isFalse();
    }
}
