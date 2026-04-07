package com.interswitch.walletapp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interswitch.walletapp.advice.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       @NonNull AccessDeniedException accessDeniedException) throws IOException {
        String errorTraceId = UUID.randomUUID().toString();
        ApiError apiError = new ApiError(
                request.getRequestURI(),
                "Forbidden",
                errorTraceId,
                HttpServletResponse.SC_FORBIDDEN,
                LocalDateTime.now()
        );
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write(objectMapper
                .writeValueAsString(apiError));
    }
}