package com.interswitch.walletapp.controllers.v1;

import com.interswitch.walletapp.constants.Permissions;
import com.interswitch.walletapp.constants.Roles;
import com.interswitch.walletapp.models.enums.CardStatus;
import com.interswitch.walletapp.models.request.CreateCardRequest;
import com.interswitch.walletapp.models.request.CreateMyCardRequest;
import com.interswitch.walletapp.models.response.CardResponse;
import com.interswitch.walletapp.services.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Card Management", description = "Endpoints for issuing, blocking, and managing virtual or physical payment cards")
@RestController
@RequestMapping("cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    @Operation(
            summary = "Issue Card (Admin)",
            description = "Allows administrators to issue a new card to any merchant account. Requires ADMIN or SUPER_ADMIN role."
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    @PreAuthorize("hasRole('"+ Roles.ADMIN +"') or hasRole('" + Roles.SUPER_ADMIN + "')")
    public CardResponse createCard(@RequestBody @Valid CreateCardRequest request) {
        return cardService.createCard(request);
    }

    @Operation(
            summary = "Issue Self-Card",
            description = "Allows a logged-in merchant to issue a card for their own account. Requires MERCHANT role."
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("me")
    @PreAuthorize("hasRole('"+ Roles.USER +"')")
    public CardResponse createCardForSelf(@RequestBody @Valid CreateMyCardRequest request) {
        return cardService.createCardForSelf(request);
    }

    @Operation(
            summary = "Block Card (Admin)",
            description = "Administrative override to block any card. Requires ADMIN or SUPER_ADMIN role."
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PatchMapping("{cardId}/block")
    @PreAuthorize("hasRole('"+ Roles.ADMIN +"') or hasRole('" + Roles.SUPER_ADMIN + "')")
    public void blockCard(@PathVariable Long cardId) {
        cardService.blockCard(cardId);
    }

    @Operation(
            summary = "Block Self-Card",
            description = "Allows a merchant to immediately block their own card. Requires MERCHANT role."
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PatchMapping("{cardId}/block/me")
    @PreAuthorize("hasRole('"+ Roles.USER +"')")
    public void blockCardForSelf(@PathVariable Long cardId) {
        cardService.blockCardForSelf(cardId);
    }

    @Operation(
            summary = "Get Card Details",
            description = "Fetch metadata for a specific card by its ID. Requires CARD_READ authority."
    )
    @GetMapping("{cardId}")
    @PreAuthorize("hasAuthority('" + Permissions.CARD_READ + "')")
    public CardResponse getCardById(@PathVariable Long cardId) {
        return cardService.getCardById(cardId);
    }

    @Operation(
            summary = "List Cards by Account",
            description = "Retrieve a paginated list of all cards linked to a specific account. Requires CARD_READ authority."
    )
    @GetMapping("account/{accountId}")
    @PreAuthorize("hasAuthority('" + Permissions.CARD_READ + "')")
    public Page<CardResponse> getCardsByAccount(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "created_at") String sortField,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDirection
    ) {
        return cardService.getCardsByAccount(accountId, page, size, sortField, sortDirection);
    }

    @Operation(
            summary = "Update Card Status",
            description = "Change the lifecycle state of a card (e.g., ACTIVE, EXPIRED). Requires CARD_UPDATE authority."
    )
    @PatchMapping("{cardId}/status")
    @PreAuthorize("hasAuthority('" + Permissions.CARD_UPDATE + "')")
    public CardResponse updateCardStatus(
            @PathVariable Long cardId,
            @RequestParam CardStatus status
    ) {
        return cardService.updateCardStatus(cardId, status);
    }

    @Operation(
            summary = "Delete Card",
            description = "Permanently remove a card record. Requires CARD_DELETE authority."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Card successfully deleted", content = @Content)
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("{cardId}")
    @PreAuthorize("hasAuthority('" + Permissions.CARD_DELETE + "')")
    public void deleteCard(@PathVariable Long cardId) {
        cardService.deleteCard(cardId);
    }
}