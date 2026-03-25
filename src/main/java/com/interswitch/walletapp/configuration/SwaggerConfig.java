package com.interswitch.walletapp.configuration;

import com.interswitch.walletapp.advice.ApiError;
import com.interswitch.walletapp.advice.ApiSuccess;
import com.interswitch.walletapp.annotation.RawResponse;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;

import java.util.Collections;
import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        final String securitySchemeName = "bearerAuth";

        Schema<?> errorSchema = new Schema<>()
                .name("ApiError")
                .addProperty("path", new StringSchema().example("/api/v1/transfers"))
                .addProperty("errorMessage", new StringSchema().example("Insufficient funds"))
                .addProperty("errorTraceId", new StringSchema().example("550e8400-e29b-41d4-a716-446655440000"))
                .addProperty("statusCode", new IntegerSchema().example(400))
                .addProperty("timestamp", new DateTimeSchema().example("2026-03-24T12:05:36Z"));

        Schema<?> apiSuccessSchema = new Schema<>()
                .name("ApiSuccess")
                .addProperty("message", new StringSchema().example("Request processed successfully"))
                .addProperty("data", new ObjectSchema())
                .addProperty("timestamp", new DateTimeSchema().example("2026-03-24T12:05:36Z"));

        return new OpenAPI()
                .info(new Info()
                        .title("Wallet API")
                        .version("1.0.0"))
                .components(new Components()
                        .addSchemas("ApiError", errorSchema)
                        .addSchemas("ApiSuccess", apiSuccessSchema)
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }

    @Bean
    public OperationCustomizer globalLayoutCustomizer() {
        return (operation, handlerMethod) -> {
            Class<?> beanType = handlerMethod.getBeanType();
            if (!beanType.isAnnotationPresent(RestController.class)) {
                return operation;
            }

            if (isPublicEndpoint(operation, handlerMethod)) {
                operation.setSecurity(Collections.emptyList());
            } else {
                operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
            }

            if (handlerMethod.hasMethodAnnotation(RawResponse.class)) {
                return operation;
            }

            Class<?> returnType = handlerMethod.getReturnType().getParameterType();
            boolean shouldWrap = !ApiSuccess.class.isAssignableFrom(returnType)
                    && !ApiError.class.isAssignableFrom(returnType)
                    && !Resource.class.isAssignableFrom(returnType)
                    && !ResponseEntity.class.isAssignableFrom(returnType);

            if (operation.getResponses() == null) {
                operation.setResponses(new ApiResponses());
            }

            operation.getResponses().forEach((status, response) -> {
                if (status.startsWith("2") && shouldWrap) {
                    wrapWithApiSuccess(response);
                } else if (status.startsWith("4") || status.startsWith("5")) {
                    applyErrorSchema(response);
                }
            });

            return operation;
        };
    }

    private boolean isPublicEndpoint(Operation operation, HandlerMethod handlerMethod) {
        String methodName = handlerMethod.getMethod().getName();
        List<String> publicMethods = List.of("login", "refresh", "registerNewUserAsMerchant");

        if (publicMethods.contains(methodName)) {
            return true;
        }

        return operation.getTags() != null && operation.getTags().contains("Public");
    }

    private void wrapWithApiSuccess(ApiResponse response) {
        Content content = response.getContent();
        if (content == null) {
            content = new Content();
            response.setContent(content);
        }

        // Find the existing media type entry, whatever it is (*/* or application/json)
        String existingKey = content.containsKey("application/json")
                ? "application/json"
                : content.keySet().stream().findFirst().orElse(null);

        Schema<?> originalSchema = null;
        if (existingKey != null) {
            originalSchema = content.get(existingKey).getSchema();
            content.remove(existingKey); // remove old entry so */* doesn't linger
        }

        if (originalSchema == null) {
            originalSchema = new ObjectSchema();
        }

        Schema<?> dataSchema = originalSchema.get$ref() != null
                ? new Schema<>().$ref(originalSchema.get$ref())
                : originalSchema;

        Schema<?> wrapperSchema = new ObjectSchema()
                .addProperty("message", new StringSchema().example("Request processed successfully"))
                .addProperty("data", dataSchema)
                .addProperty("timestamp", new DateTimeSchema().example("2026-03-24T12:05:36Z"));

        content.addMediaType("application/json", new MediaType().schema(wrapperSchema));
    }

    private void applyErrorSchema(ApiResponse response) {
        Content content = response.getContent();
        if (content == null) {
            content = new Content();
            response.setContent(content);
        }
        Schema<?> errorRef = new Schema<>().$ref("#/components/schemas/ApiError");
        content.addMediaType("application/json", new MediaType().schema(errorRef));
    }
}