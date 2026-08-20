package com.prince.agentic.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login payload. Validation is intentionally lenient (presence + length only, no format
 * checks) so it does not leak account-policy details; authentication failures are generic.
 */
public record LoginRequest(

        @NotBlank(message = "email must not be blank")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        @NotBlank(message = "password must not be blank")
        @Size(max = 72, message = "password must be at most 72 characters")
        String password) {
}
