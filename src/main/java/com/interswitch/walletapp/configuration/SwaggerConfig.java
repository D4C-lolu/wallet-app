package com.interswitch.walletapp.configuration;

import com.interswitch.walletapp.annotation.RawResponse;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

        return new OpenAPI()
                .info(new Info()
                        .title("Wallet API")
                        .version("1.0.0"))
                .components(new Components()
                        .addSchemas("ApiError", errorSchema)
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

            if (isPublicEndpoint(operation, handlerMethod)) {
                operation.setSecurity(Collections.emptyList());
            } else {
                operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
            }

            if (handlerMethod.hasMethodAnnotation(RawResponse.class)) {
                return operation;
            }

            operation.getResponses().forEach((status, response) -> {
                if (status.startsWith("2")) {
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

        // List of method names that should NOT have a lock (Public)
        List<String> publicMethods = List.of("login", "refresh", "registerMerchant");

        if (publicMethods.contains(methodName)) {
            return true;
        }

        if (operation.getTags() == null) return false;
        return operation.getTags().contains("Public");
    }

    private void wrapWithApiSuccess(ApiResponse response) {
        Content content = response.getContent();
        if (content != null && content.containsKey("application/json")) {
            MediaType mediaType = content.get("application/json");
            Schema<?> originalSchema = mediaType.getSchema();

            Schema<?> wrapperSchema = new Schema<>()
                    .addProperty("message", new StringSchema().example("Request processed successfully"))
                    .addProperty("data", originalSchema)
                    .addProperty("timestamp", new DateTimeSchema().example("2026-03-24T12:05:36Z"));

            mediaType.setSchema(wrapperSchema);
        }
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