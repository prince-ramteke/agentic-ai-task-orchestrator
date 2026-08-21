package com.prince.agentic.customer.dto;

import com.prince.agentic.customer.CustomerStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Full-replacement update (PUT). status is required; omitting email/phone clears them. */
public record CustomerUpdateRequest(

        @NotBlank(message = "name must not be blank")
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,

        @Email(message = "email must be a valid address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        @Size(max = 30, message = "phone must be at most 30 characters")
        @Pattern(regexp = "[0-9+()\\-\\s]*", message = "phone may contain only digits, spaces, and + - ( )")
        String phone,

        @NotNull(message = "status is required")
        CustomerStatus status) {
}
