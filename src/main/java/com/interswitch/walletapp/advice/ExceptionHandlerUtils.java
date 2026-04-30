package com.interswitch.walletapp.advice;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.time.LocalDateTime.now;
import static org.springframework.http.HttpStatus.*;

public class ExceptionHandlerUtils {

    public static ResponseEntity<ApiError> badRequest(String message, String uri, String errorTraceId) {
        final var apiError = new ApiError(uri, message, errorTraceId, BAD_REQUEST.value(), now());
        return new ResponseEntity<>(apiError, BAD_REQUEST);
    }

    public static ResponseEntity<ApiError> conflict(String message, String uri, String errorTraceId) {
        final var apiError = new ApiError(uri, message, errorTraceId, CONFLICT.value(), now());
        return new ResponseEntity<>(apiError, CONFLICT);
    }

    public static ResponseEntity<ApiError> notFound(String message, String uri, String errorTraceId) {
        final var apiError = new ApiError(uri, message, errorTraceId, NOT_FOUND.value(), now());
        return new ResponseEntity<>(apiError, NOT_FOUND);
    }

    public static String resolveMessage(HttpMessageNotReadableException e) {
        if (e.getCause() instanceof InvalidFormatException invalidFormat) {
            return resolveInvalidFormatMessage(invalidFormat);
        }
        return "Invalid request format";
    }

    public static String resolveValidationMessage(MethodArgumentNotValidException e) {
        BindingResult result = e.getBindingResult();

        Stream<String> fieldErrors = result.getFieldErrors().stream()
                .map(error -> String.format("'%s': %s", error.getField(),
                        Objects.toString(error.getDefaultMessage(), "")));

        Stream<String> globalErrors = result.getGlobalErrors().stream()
                .map(error -> Objects.toString(error.getDefaultMessage(), "Validation failed"));

        String message = Stream.concat(fieldErrors, globalErrors)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(", "));

        return message.isBlank() ? "Validation failed" : message;
    }

    private static String resolveInvalidFormatMessage(InvalidFormatException e) {
        String field = extractFieldName(e);
        String value = e.getValue() != null ? e.getValue().toString() : "null";
        Class<?> type = e.getTargetType();

        if (type.isEnum())                return invalidEnum(field, value, type);
        if (type == LocalDate.class)      return invalidDate(field, value);
        if (type == OffsetDateTime.class) return invalidDateTime(field, value);

        return String.format("Invalid value for field '%s'", field);
    }

    public static String resolveHandlerValidationMessage(HandlerMethodValidationException e) {
        String message = e.getParameterValidationResults().stream()
                .flatMap(result -> {
                    String paramName = Objects.toString(result.getMethodParameter().getParameterName(), "unknown");
                    return result.getResolvableErrors().stream()
                            .map(MessageSourceResolvable::getDefaultMessage)
                            .filter(Objects::nonNull)
                            .map(error -> String.format("'%s': %s", paramName, error));
                })
                .collect(Collectors.joining(", "));

        return message.isBlank() ? "Validation failed" : message;
    }

    public static String resolveConstraintViolationMessage(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> String.format("'%s': %s", extractFieldName(v.getPropertyPath()), v.getMessage()))
                .collect(Collectors.joining(", "));

        return message.isBlank() ? "Validation failed" : message;
    }

    private static String extractFieldName(Path path) {
        String raw = path.toString();
        int dot = raw.lastIndexOf('.');
        return dot >= 0 ? raw.substring(dot + 1) : raw;
    }


    private static String extractFieldName(InvalidFormatException e) {
        return e.getPath().isEmpty() ? "unknown" : e.getPath().getFirst().getPropertyName();
    }

    private static String invalidEnum(String field, String value, Class<?> type) {
        String validValues = Arrays.stream(type.getEnumConstants())
                .map(Object::toString)
                .collect(Collectors.joining(", "));
        return String.format("Invalid value '%s' for field '%s'. Accepted values: [%s]", value, field, validValues);
    }

    private static String invalidDate(String field, String value) {
        return String.format("Invalid date for field '%s'. Value '%s' must be in format yyyy-MM-dd (e.g., 2024-12-31)", field, value);
    }

    private static String invalidDateTime(String field, String value) {
        return String.format("Invalid datetime for field '%s'. Value '%s' must be in ISO-8601 format (e.g., 2024-12-31T14:30:00Z)", field, value);
    }
}
