package com.prince.agentic.customer.dto;

import com.prince.agentic.customer.CustomerStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create a customer. Owner assigned server-side (no owner field). status defaults to ACTIVE. */
public record CustomerCreateRequest(

        @NotBlank(message = "name must not be blank")
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,

        @Email(message = "email must be a valid address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        @Size(max = 30, message = "phone must be at most 30 characters")
        @Pattern(regexp = "[0-9+()\\-\\s]*", message = "phone may contain only digits, spaces, and + - ( )")
        String phone,

        CustomerStatus status) {
}
