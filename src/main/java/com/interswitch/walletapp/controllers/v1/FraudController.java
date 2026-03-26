package com.interswitch.walletapp.controllers.v1;

import com.interswitch.walletapp.constants.Permissions;
import com.interswitch.walletapp.constants.Roles;
import com.interswitch.walletapp.models.enums.FraudStatus;
import com.interswitch.walletapp.models.projections.FraudEvaluationContext;
import com.interswitch.walletapp.models.response.FraudAttemptResponse;
import com.interswitch.walletapp.services.FraudDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("fraud")
@RequiredArgsConstructor
@Tag(name = "Fraud Detection", description = "Real-time fraud evaluation and audit trail management")
public class FraudController {

    private final FraudDetectionService fraudDetectionService;

    @PostMapping("evaluate")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAuthority('" + Permissions.TRANSACTION_CREATE + "')")
    @Operation(
            summary = "Evaluate a transaction for fraud",
            description = "Runs a transaction through the full fraud rule engine. Hard blocks return BLOCKED immediately. Soft flags return SUSPICIOUS but allow the transaction through."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evaluation complete",
                    content = @Content(schema = @Schema(implementation = FraudStatus.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public FraudStatus evaluate(@RequestBody @Valid FraudEvaluationContext request) {
        return fraudDetectionService.evaluate(request);
    }

    @GetMapping("attempts")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('" + Roles.SUPER_ADMIN + "')")
    @Operation(
            summary = "List all fraud attempts",
            description = "Returns a paginated audit log of all evaluated transactions including their fraud status and triggered flags. Restricted to SUPER_ADMIN."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fraud attempts retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Restricted to SUPER_ADMIN only")
    })
    public Page<FraudAttemptResponse> getFraudAttempts(
            @Parameter(description = "Page number, 1-based", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Number of results per page", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        return fraudDetectionService.getFraudAttempts(page, size);
    }
}