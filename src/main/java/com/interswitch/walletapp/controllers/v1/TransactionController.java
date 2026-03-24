package com.interswitch.walletapp.controllers.v1;

import com.interswitch.walletapp.constants.Roles;
import com.interswitch.walletapp.models.response.TransactionResponse;
import com.interswitch.walletapp.services.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Transaction History", description = "Endpoints for auditing financial movements and viewing account-specific transaction logs")
@RestController
@RequestMapping("transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(
            summary = "Get Transaction Detail (Admin)",
            description = "Administrative lookup for a single transaction by ID. Requires ADMIN or SUPER_ADMIN role."
    )
    @GetMapping("{transactionId}")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "') or hasRole('" + Roles.SUPER_ADMIN + "')")
    public TransactionResponse getTransactionById(@PathVariable Long transactionId) {
        return transactionService.getTransactionById(transactionId);
    }

    @Operation(
            summary = "My Account Transactions",
            description = "Allows a merchant to view the transaction history for their own account. Requires MERCHANT role."
    )
    @GetMapping("me/account/{accountId}")
    @PreAuthorize("hasRole('" + Roles.MERCHANT + "')")
    public Page<TransactionResponse> getMyTransactionsByAccount(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return transactionService.getTransactionsByAccountForSelf(accountId, page, size);
    }

    @Operation(
            summary = "List Account Transactions (Admin)",
            description = "Administrative paginated view of all transactions for any given account ID. Requires ADMIN or SUPER_ADMIN role."
    )
    @GetMapping("account/{accountId}")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "') or hasRole('" + Roles.SUPER_ADMIN + "')")
    public Page<TransactionResponse> getTransactionsByAccount(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return transactionService.getTransactionsByAccount(accountId, page, size);
    }
}