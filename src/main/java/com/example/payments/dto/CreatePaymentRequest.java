package com.example.payments.dto;

import com.example.payments.validation.ValidCurrency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Request payload for creating a new payment")
public record CreatePaymentRequest(

        @Schema(description = "Customer ID initiating the payment", example = "cust_123")
        @NotBlank(message = "Customer ID must not be blank")
        String customerId,

        @Schema(description = "Payment amount (must be positive, scale 2)", example = "1500.00")
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        @Schema(description = "Three-letter ISO 4217 currency code", example = "INR")
        @NotBlank(message = "Currency must not be blank")
        @ValidCurrency
        String currency,

        @Schema(description = "Optional transaction description or order reference", example = "Order #12345")
        @Size(max = 255, message = "Description must not exceed 255 characters")
        String description
) {
}
