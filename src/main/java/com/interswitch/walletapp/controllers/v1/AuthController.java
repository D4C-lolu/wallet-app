package com.interswitch.walletapp.controllers.v1;

import com.interswitch.walletapp.models.request.LoginRequest;
import com.interswitch.walletapp.models.response.AuthResponse;
import com.interswitch.walletapp.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "Endpoints for identity management, session handling, and token lifecycle")
@RestController
@RequestMapping("auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private static final String authHeader = "Authorization";
    private static final String refreshTokenHeader = "Refresh-Token";

    @Operation(
            summary = "Login",
            description = "Authenticates user credentials and returns access and refresh tokens. Public access."
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("login")
    public AuthResponse login(@RequestBody @Valid LoginRequest request) {
        return authService.login(request);
    }

    @Operation(
            summary = "Refresh Token",
            description = "Generates a new access token using a valid refresh token. Public access."
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("refresh")
    public AuthResponse refresh(@RequestHeader(refreshTokenHeader) String refreshToken) {
        return authService.refresh(refreshToken);
    }

    @Operation(
            summary = "Logout",
            description = "Invalidates the specific access and refresh tokens provided. Requires authentication."
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("logout")
    public void logout(
            @RequestHeader(authHeader) String accessTokenHeader,
            @RequestHeader(refreshTokenHeader) String refreshToken
    ) {
        authService.logout(accessTokenHeader, refreshToken);
    }

    @Operation(
            summary = "Logout all devices",
            description = "Terminates all active sessions and invalidates all tokens for the current user. Requires authentication."
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("logout-all")
    public void logoutAll() {
        authService.logoutAll();
    }
}