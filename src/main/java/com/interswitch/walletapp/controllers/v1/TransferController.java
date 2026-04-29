package com.interswitch.walletapp.controllers.v1;

import com.interswitch.walletapp.annotation.Idempotent;
import com.interswitch.walletapp.annotation.ObserveParam;
import com.interswitch.walletapp.constants.Roles;
import com.interswitch.walletapp.models.request.TransferRequest;
import com.interswitch.walletapp.models.response.TransferResponse;
import com.interswitch.walletapp.services.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Transfer Management", description = "Endpoints for initiating and auditing fund transfers between accounts")
@RestController
@RequestMapping("transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @Operation(
            summary = "Initiate Transfer (Self)",
            description = "Allows an authenticated merchant to initiate a fund transfer from their own account. Validation includes balance and tier limit checks."
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("me")
    public TransferResponse transfer(
            @RequestHeader("X-Idempotency-Key") @NotBlank String reference,
            @Valid @RequestBody TransferRequest request
    ) {
        return transferService.transferForSelf(request, reference);
    }

    @Operation(
            summary = "Get Transfer Detail (Admin)",
            description = "Administrative lookup for a specific transfer's metadata and status. Requires ADMIN or SUPER_ADMIN role."
    )
    @GetMapping("{transferId}")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "') or hasRole('" + Roles.SUPER_ADMIN + "')")
    public TransferResponse getTransferById(@ObserveParam("transferId") @PathVariable Long transferId) {
        return transferService.getTransferById(transferId);
    }

    @Operation(
            summary = "My Account Transfers",
            description = "Allows a merchant to view the transfer history for an account they own. Requires MERCHANT role."
    )
    @GetMapping("me/account/{accountId}")
    @PreAuthorize("hasRole('" + Roles.USER + "')")
    public Page<TransferResponse> getMyTransfersByAccount(
            @ObserveParam("accountId") @PathVariable Long accountId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return transferService.getTransfersByAccountForSelf(accountId, page, size);
    }

    @Operation(
            summary = "List Account Transfers (Admin)",
            description = "Administrative paginated view of all transfers for any given account ID. Requires ADMIN or SUPER_ADMIN role."
    )
    @GetMapping("account/{accountId}")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "') or hasRole('" + Roles.SUPER_ADMIN + "')")
    public Page<TransferResponse> getTransfersByAccount(
            @ObserveParam("accountId") @PathVariable Long accountId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return transferService.getTransfersByAccount(accountId, page, size);
    }
}