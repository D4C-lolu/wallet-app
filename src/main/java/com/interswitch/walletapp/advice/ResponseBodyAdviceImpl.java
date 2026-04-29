package com.interswitch.walletapp.advice;

import com.interswitch.walletapp.exceptions.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.time.LocalDateTime.now;
import static org.springframework.http.HttpStatus.*;

@Slf4j
@RestControllerAdvice
public class ResponseBodyAdviceImpl  {

    @ExceptionHandler({
            BadRequestException.class,
            IllegalArgumentException.class,
            IllegalStateException.class,
            MaxUploadSizeExceededException.class,
            ConversionFailedException.class,
            HttpRequestMethodNotSupportedException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiError> handleBadRequestException(Exception e, HttpServletRequest request) {
        final var errorTraceId = UUID.randomUUID().toString();
        final var uri = request.getRequestURI();
        log.error("An error has occurred at {} with id {}", uri, errorTraceId);
        log.error(e.getMessage(), e);
        return ExceptionHandlerUtils.badRequest(e.getMessage(), uri, errorTraceId);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingRequestHeaderException(MissingRequestHeaderException e, HttpServletRequest request) {
        final var errorTraceId = UUID.randomUUID().toString();
        final var uri = request.getRequestURI();

        if ("Authorization".equalsIgnoreCase(e.getHeaderName())) {
            final var apiError = new ApiError(uri, "Unauthorized", errorTraceId, UNAUTHORIZED.value(), now());
            return new ResponseEntity<>(apiError, UNAUTHORIZED);
        }

        return ExceptionHandlerUtils.badRequest(e.getMessage(), uri, errorTraceId);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadableException(HttpMessageNotReadableException e, HttpServletRequest request) {
        final var errorTraceId = UUID.randomUUID().toString();
        final var uri = request.getRequestURI();
        log.error("Invalid request format error at {} with id {}", uri, errorTraceId);
        log.error(e.getMessage(), e);
        return ExceptionHandlerUtils.badRequest(ExceptionHandlerUtils.resolveMessage(e), uri, errorTraceId);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValidException(MethodArgumentNotValidException e, HttpServletRequest request) {
        final var message = ExceptionHandlerUtils.resolveValidationMessage(e);
        final var apiError = new ApiError(request.getRequestURI(), message, null, BAD_REQUEST.value(), now());
        return new ResponseEntity<>(apiError, BAD_REQUEST);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflictException(ConflictException e, HttpServletRequest request) {
        final var errorTraceId = UUID.randomUUID().toString();
        final var uri = request.getRequestURI();
        log.error("Conflict error at {} with id {}", uri, errorTraceId);
        log.error(e.getMessage(), e);
        return ExceptionHandlerUtils.conflict(e.getMessage(), uri, errorTraceId);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleHandlerMethodValidationException(HandlerMethodValidationException e, HttpServletRequest request) {
        final var message = ExceptionHandlerUtils.resolveHandlerValidationMessage(e);
        final var apiError = new ApiError(request.getRequestURI(), message, null, BAD_REQUEST.value(), now());
        return new ResponseEntity<>(apiError, BAD_REQUEST);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentialsException(BadCredentialsException e, HttpServletRequest request) {
        final var errorTraceId = UUID.randomUUID().toString();
        final var uri = request.getRequestURI();
        final var apiError = new ApiError(uri, "Invalid email or password", errorTraceId, UNAUTHORIZED.value(), now());
        return new ResponseEntity<>(apiError, UNAUTHORIZED);
    }

    @ExceptionHandler({LockedException.class, DisabledException.class})
    public ResponseEntity<ApiError> handleAccountStatusException(Exception e, HttpServletRequest request) {
        final var errorTraceId = UUID.randomUUID().toString();
        final var uri = request.getRequestURI();
        final var apiError = new ApiError(uri, "Account is not accessible", errorTraceId, FORBIDDEN.value(), now());
        return new ResponseEntity<>(apiError, FORBIDDEN);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> handleUnauthenticatedException(UnauthorizedException e, HttpServletRequest request) {
        final var errorTraceId = UUID.randomUUID().toString();
        final var uri = request.getRequestURI();
        log.error("Unauthenticated access at {} with id {}", uri, errorTraceId);
        final var apiError = new ApiError(uri, "Unauthorized", errorTraceId, UNAUTHORIZED.value(), now());
        return new ResponseEntity<>(apiError, UNAUTHORIZED);
    }

    @ExceptionHandler({
            AccessDeniedException.class,
            AuthorizationDeniedException.class,
            ForbiddenException.class
    })
    public ResponseEntity<ApiError> handleForbiddenException(Exception e, HttpServletRequest request) {
        final var errorTraceId = UUID.randomUUID().toString();
        final var uri = request.getRequestURI();
        log.error("Forbidden access at {} with id {}", uri, errorTraceId);
        final var apiError = new ApiError(uri, e.getMessage(), errorTraceId, FORBIDDEN.value(), now());
        return new ResponseEntity<>(apiError, FORBIDDEN);
    }

    @ExceptionHandler(FraudDetectedException.class)
    public ResponseEntity<ApiError> handleFraudDetectedException(FraudDetectedException e, HttpServletRequest request) {
        final var errorTraceId = UUID.randomUUID().toString();
        final var uri = request.getRequestURI();
        log.warn("Fraud detected at {} with id {}: {}", uri, errorTraceId, e.getMessage());
        final var apiError = new ApiError(uri, e.getMessage(), errorTraceId, FORBIDDEN.value(), now());
        return new ResponseEntity<>(apiError, FORBIDDEN);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiError> handleInvalidTokenException(InvalidTokenException e, HttpServletRequest request) {
        final var errorTraceId = UUID.randomUUID().toString();
        final var uri = request.getRequestURI();
        log.error("Invalid token at {} with id {}", uri, errorTraceId);
        final var apiError = new ApiError(uri, e.getMessage(), errorTraceId, UNAUTHORIZED.value(), now());
        return new ResponseEntity<>(apiError, UNAUTHORIZED);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolationException(ConstraintViolationException e, HttpServletRequest request) {
        final var errorTraceId = UUID.randomUUID().toString();
        final var uri = request.getRequestURI();
        final var message = e.getConstraintViolations().stream()
                .map(violation -> {
                    String field = violation.getPropertyPath().toString();
                    field = field.contains(".") ? field.substring(field.lastIndexOf('.') + 1) : field;
                    return field + " " + violation.getMessage();
                })
                .collect(Collectors.joining(", "));
        log.error("Constraint violation at {} with id {}", uri, errorTraceId);
        return ExceptionHandlerUtils.badRequest(message, uri, errorTraceId);
    }

    @ExceptionHandler({
            NoSuchElementException.class,
            NotFoundException.class,
            NoResourceFoundException.class,
    })
    public ResponseEntity<ApiError> handleNotFoundException(Exception e, HttpServletRequest request) {
        final var errorTraceId = UUID.randomUUID().toString();
        final var uri = request.getRequestURI();
        log.error("An error has occurred at {} with id {}", uri, errorTraceId);
        log.error(e.getMessage(), e);
        return ExceptionHandlerUtils.notFound(e.getMessage(), uri, errorTraceId);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleException(Exception e, HttpServletRequest request) {
        final var requestURI = request.getRequestURI();
        final var errorTraceId = UUID.randomUUID().toString();
        log.error("An internal server error has occurred at {} with id {}", requestURI, errorTraceId);
        log.error(ExceptionUtils.getStackTrace(e));
        final var apiError = new ApiError(requestURI, "An internal server error has occurred", errorTraceId, INTERNAL_SERVER_ERROR.value(), now());
        return new ResponseEntity<>(apiError, INTERNAL_SERVER_ERROR);
    }
}