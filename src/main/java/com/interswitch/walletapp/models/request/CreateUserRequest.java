package com.interswitch.walletapp.models.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUserRequest(
        @NotBlank String firstname,
        @NotBlank String lastname,
        String othername,
        @NotBlank @Email String email,
        @NotBlank String phone,
        @NotBlank String password,
        @NotNull Long roleId
) {}

