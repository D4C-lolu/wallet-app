package com.interswitch.walletapp.controllers.v1;

import com.interswitch.walletapp.constants.Permissions;
import com.interswitch.walletapp.models.enums.KycStatus;
import com.interswitch.walletapp.models.enums.MerchantStatus;
import com.interswitch.walletapp.models.request.CreateMerchantRequest;
import com.interswitch.walletapp.models.request.MerchantSignupRequest;
import com.interswitch.walletapp.models.request.UpdateMerchantRequest;
import com.interswitch.walletapp.models.response.MerchantResponse;
import com.interswitch.walletapp.services.MerchantService;
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

@Tag(name = "Merchant Management", description = "Endpoints for merchant lifecycle, KYC verification, and tier management")
@RestController
@RequestMapping("merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    @Operation(
            summary = "Create Merchant (Admin)",
            description = "Allows an administrator to manually create a merchant profile. Requires MERCHANT_CREATE authority."
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.MERCHANT_CREATE + "')")
    public MerchantResponse createMerchant(@RequestBody @Valid CreateMerchantRequest request) {
        return merchantService.createMerchant(request);
    }

    @Operation(
            summary = "Public Registration",
            description = "Public endpoint for new users to sign up as merchants. No authentication required."
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("register")
    public MerchantResponse registerNewUserAsMerchant(@RequestBody @Valid MerchantSignupRequest request) {
        return merchantService.registerNewUserAsMerchant(request);
    }

    @Operation(
            summary = "Self-Registration",
            description = "Allows an existing authenticated user to promote their account to a merchant profile."
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("self-register")
    public MerchantResponse selfRegisterExistingUser(@RequestParam(required = false) String address) {
        return merchantService.selfRegisterExistingUser(address);
    }

    @Operation(
            summary = "List All Merchants",
            description = "Retrieve a paginated list of all registered merchants. Requires MERCHANT_READ authority."
    )
    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.MERCHANT_READ + "')")
    public Page<MerchantResponse> getAllMerchants(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDirection
    ) {
        return merchantService.getAllMerchants(page, size, sortField, sortDirection);
    }

    @Operation(
            summary = "Filter by Status",
            description = "List merchants based on their account status (e.g., ACTIVE, PENDING). Requires MERCHANT_READ authority."
    )
    @GetMapping("status")
    @PreAuthorize("hasAuthority('" + Permissions.MERCHANT_READ + "')")
    public Page<MerchantResponse> getMerchantsByStatus(
            @RequestParam MerchantStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDirection
    ) {
        return merchantService.getMerchantsByStatus(status, page, size, sortField, sortDirection);
    }

    @Operation(
            summary = "Filter by KYC Status",
            description = "List merchants based on their verification level. Requires MERCHANT_READ authority."
    )
    @GetMapping("kyc-status")
    @PreAuthorize("hasAuthority('" + Permissions.MERCHANT_READ + "')")
    public Page<MerchantResponse> getMerchantsByKycStatus(
            @RequestParam KycStatus kycStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDirection
    ) {
        return merchantService.getMerchantsByKycStatus(kycStatus, page, size, sortField, sortDirection);
    }

    @Operation(
            summary = "Get Merchant by ID",
            description = "Fetch full profile details for a specific merchant. Requires MERCHANT_READ authority."
    )
    @GetMapping("{merchantId}")
    @PreAuthorize("hasAuthority('" + Permissions.MERCHANT_READ + "')")
    public MerchantResponse getMerchantById(@PathVariable Long merchantId) {
        return merchantService.getMerchantById(merchantId);
    }

    @Operation(
            summary = "Update Merchant Profile",
            description = "Update core merchant information. Requires MERCHANT_UPDATE authority."
    )
    @PutMapping("{merchantId}")
    @PreAuthorize("hasAuthority('" + Permissions.MERCHANT_UPDATE + "')")
    public MerchantResponse updateMerchant(
            @PathVariable Long merchantId,
            @RequestBody @Valid UpdateMerchantRequest request
    ) {
        return merchantService.updateMerchant(merchantId, request);
    }

    @Operation(
            summary = "Update Account Status",
            description = "Change merchant status (Activate/Suspend). Requires MERCHANT_UPDATE authority."
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PatchMapping("{merchantId}/status")
    @PreAuthorize("hasAuthority('" + Permissions.MERCHANT_UPDATE + "')")
    public MerchantResponse updateMerchantStatus(
            @PathVariable Long merchantId,
            @RequestParam MerchantStatus status
    ) {
        return merchantService.updateMerchantStatus(merchantId, status);
    }

    @Operation(
            summary = "Update KYC Level",
            description = "Manually verify or reject merchant KYC documents. Requires MERCHANT_KYC authority."
    )
    @PatchMapping("{merchantId}/kyc")
    @PreAuthorize("hasAuthority('" + Permissions.MERCHANT_KYC + "')")
    public MerchantResponse updateKycStatus(
            @PathVariable Long merchantId,
            @RequestParam KycStatus kycStatus
    ) {
        return merchantService.updateKycStatus(merchantId, kycStatus);
    }

    @Operation(
            summary = "Upgrade Tier",
            description = "Increase merchant transaction limits. Requires MERCHANT_UPDATE authority."
    )
    @PatchMapping("{merchantId}/tier/upgrade")
    @PreAuthorize("hasAuthority('" + Permissions.MERCHANT_UPDATE + "')")
    public MerchantResponse upgradeTier(@PathVariable Long merchantId) {
        return merchantService.upgradeTier(merchantId);
    }

    @Operation(
            summary = "Downgrade Tier",
            description = "Reduce merchant transaction limits. Requires MERCHANT_UPDATE authority."
    )
    @PatchMapping("{merchantId}/tier/downgrade")
    @PreAuthorize("hasAuthority('" + Permissions.MERCHANT_UPDATE + "')")
    public MerchantResponse downgradeTier(@PathVariable Long merchantId) {
        return merchantService.downgradeTier(merchantId);
    }

    @Operation(
            summary = "Delete Merchant",
            description = "Soft delete a merchant record. Requires MERCHANT_DELETE authority."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Merchant successfully deleted", content = @Content)
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("{merchantId}")
    @PreAuthorize("hasAuthority('" + Permissions.MERCHANT_DELETE + "')")
    public void deleteMerchant(@PathVariable Long merchantId) {
        merchantService.deleteMerchant(merchantId);
    }
}