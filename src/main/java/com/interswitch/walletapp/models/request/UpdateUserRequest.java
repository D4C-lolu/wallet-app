package com.interswitch.walletapp.models.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record UpdateUserRequest(
        @NotBlank String firstname,
        @NotBlank String lastname,
        String othername,
        @NotBlank String phone,
        @NotBlank @NotEmpty String email
) {}
