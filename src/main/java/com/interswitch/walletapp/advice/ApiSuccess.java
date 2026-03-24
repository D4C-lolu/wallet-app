package com.interswitch.walletapp.advice;

import java.time.LocalDateTime;

public record ApiSuccess(String message, Object data, LocalDateTime timestamp) {
    public ApiSuccess(String message, Object data) {
        this(message, data, LocalDateTime.now());
    }
}