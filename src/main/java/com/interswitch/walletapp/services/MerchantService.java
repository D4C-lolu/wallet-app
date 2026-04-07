package com.interswitch.walletapp.services;

import com.interswitch.walletapp.constants.Roles;
import com.interswitch.walletapp.dao.MerchantDao;
import com.interswitch.walletapp.exceptions.BadRequestException;
import com.interswitch.walletapp.exceptions.ConflictException;
import com.interswitch.walletapp.exceptions.NotFoundException;
import com.interswitch.walletapp.models.enums.KycStatus;
import com.interswitch.walletapp.models.enums.MerchantStatus;
import com.interswitch.walletapp.models.enums.MerchantTier;
import com.interswitch.walletapp.models.request.CreateMerchantRequest;
import com.interswitch.walletapp.models.request.CreateUserRequest;
import com.interswitch.walletapp.models.request.MerchantSignupRequest;
import com.interswitch.walletapp.models.request.UpdateMerchantRequest;
import com.interswitch.walletapp.models.response.MerchantResponse;
import com.interswitch.walletapp.models.response.UserResponse;
import com.interswitch.walletapp.models.projections.MerchantValidationResult;
import com.interswitch.walletapp.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantDao merchantDao;
    private final UserService userService;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "user_id", "kyc_status", "merchant_status", "tier", "created_at", "updated_at"
    );

    @Transactional
    public MerchantResponse createMerchant(CreateMerchantRequest request) {

        MerchantValidationResult validation = merchantDao.validateMerchantCreation(
                request.userId(),
                MerchantTier.TIER_1.name()
        );

        if (validation.merchantExists()) {
            throw new ConflictException("User already has a merchant account");
        }
        if (!validation.userExists()) {
            throw new NotFoundException("User not found");
        }
        if (!validation.tierExists()) {
            throw new NotFoundException("Tier configuration not found for TIER_1");
        }

        Long createdBy = SecurityUtil.findCurrentUserId().orElse(null);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", request.userId())
                .addValue("address", request.address())
                .addValue("kycStatus", KycStatus.PENDING.name())
                .addValue("merchantStatus", MerchantStatus.INACTIVE.name())
                .addValue("tier", MerchantTier.TIER_1.name())
                .addValue("createdBy", createdBy);

        Long id = merchantDao.insert(params);
        return getMerchantById(id);
    }

    @Transactional
    public MerchantResponse selfRegisterExistingUser(String address) {
        Long userId = SecurityUtil.getCurrentUserId();
        return createMerchant(new CreateMerchantRequest(userId, address));
    }

    @Transactional
    public MerchantResponse registerNewUserAsMerchant(MerchantSignupRequest request) {
        Long roleId = merchantDao.findRoleIdByName(Roles.USER)
                .orElseThrow(() -> new NotFoundException("Merchant role not found"));

        UserResponse newUser = userService.createUser(new CreateUserRequest(
                request.firstname(),
                request.lastname(),
                request.othername(),
                request.email(),
                request.phone(),
                request.password(),
                roleId
        ));

        return createMerchant(new CreateMerchantRequest(newUser.id(), request.address()));
    }

    @Transactional
    public MerchantResponse updateMerchant(Long merchantId, UpdateMerchantRequest request) {
        MerchantResponse existing = getMerchantById(merchantId);
        Long updatedBy = SecurityUtil.findCurrentUserId().orElse(null);
        String newAddress = request.address() != null ? request.address() : existing.address();

        merchantDao.updateAddress(merchantId, newAddress, updatedBy);

        return buildUpdateResponse(existing, newAddress, existing.kycStatus(), existing.merchantStatus(), existing.tier());
    }

    @Transactional
    public MerchantResponse updateKycStatus(Long merchantId, KycStatus kycStatus) {
        MerchantResponse existing = getMerchantById(merchantId);

        if (existing.kycStatus() == KycStatus.APPROVED) {
            throw new BadRequestException("Merchant KYC is already approved");
        }

        MerchantStatus newStatus = switch (kycStatus) {
            case APPROVED -> MerchantStatus.ACTIVE;
            case REJECTED -> MerchantStatus.INACTIVE;
            default -> existing.merchantStatus();
        };

        Long updatedBy = SecurityUtil.findCurrentUserId().orElse(null);
        merchantDao.updateKycStatus(merchantId, kycStatus.name(), updatedBy);

        return buildUpdateResponse(existing, existing.address(), kycStatus, newStatus, existing.tier());
    }

    @Transactional
    public MerchantResponse upgradeTier(Long merchantId) {
        MerchantResponse existing = getMerchantById(merchantId);

        if (existing.kycStatus() != KycStatus.APPROVED) {
            throw new BadRequestException("Merchant must be KYC approved before upgrading tier");
        }

        MerchantTier nextTier = switch (existing.tier()) {
            case TIER_1 -> MerchantTier.TIER_2;
            case TIER_2 -> MerchantTier.TIER_3;
            case TIER_3 -> throw new BadRequestException("Merchant is already on the highest tier");
        };

        if (!merchantDao.tierExists(nextTier.name())) {
            throw new NotFoundException("Tier configuration not found for " + nextTier);
        }

        merchantDao.updateTier(merchantId, nextTier.name(), SecurityUtil.findCurrentUserId().orElse(null));
        return buildUpdateResponse(existing, existing.address(), existing.kycStatus(), existing.merchantStatus(), nextTier);
    }

    @Transactional
    public MerchantResponse downgradeTier(Long merchantId) {
        MerchantResponse existing = getMerchantById(merchantId);

        MerchantTier previousTier = switch (existing.tier()) {
            case TIER_3 -> MerchantTier.TIER_2;
            case TIER_2 -> MerchantTier.TIER_1;
            case TIER_1 -> throw new BadRequestException("Merchant is already on the lowest tier");
        };

        if (!merchantDao.tierExists(previousTier.name())) {
            throw new NotFoundException("Tier configuration not found for " + previousTier);
        }

        merchantDao.updateTier(merchantId, previousTier.name(), SecurityUtil.findCurrentUserId().orElse(null));
        return buildUpdateResponse(existing, existing.address(), existing.kycStatus(), existing.merchantStatus(), previousTier);
    }

    @Transactional
    public Page<MerchantResponse> getMerchantsByStatus(MerchantStatus status, int page, int size, String sortField, Sort.Direction sortDirection) {
        return merchantDao.findByStatus(status, page, size, sortField, sortDirection);
    }

    @Transactional
    public Page<MerchantResponse> getMerchantsByKycStatus(KycStatus kycStatus, int page, int size, String sortField, Sort.Direction sortDirection) {
        return merchantDao.findByKycStatus(kycStatus, page, size, sortField, sortDirection);
    }


    @Transactional
    public MerchantResponse updateMerchantStatusAndKycStatus(Long merchantId, MerchantStatus merchantStatus, KycStatus kycStatus) {
        MerchantResponse existing = getMerchantById(merchantId);
        Long updatedBy = SecurityUtil.findCurrentUserId().orElse(null);

        merchantDao.updateMerchantStatusAndKycStatus(merchantId, merchantStatus.name(), kycStatus.name(), updatedBy);

        return new MerchantResponse(
                existing.id(), existing.address(), kycStatus, merchantStatus, existing.tier(),
                existing.userId(), existing.userFirstname(), existing.userLastname(),
                existing.userEmail(), existing.userPhone(), existing.userStatus(),
                existing.createdAt(), OffsetDateTime.now()
        );
    }

    @Transactional
    public MerchantResponse updateMerchantStatus(Long merchantId, MerchantStatus status) {
        MerchantResponse existing = getMerchantById(merchantId);
        Long updatedBy = SecurityUtil.findCurrentUserId().orElse(null);

        if (existing.kycStatus() != KycStatus.APPROVED && status == MerchantStatus.ACTIVE) {
            throw new BadRequestException("Merchant must be KYC approved before activation");
        }

        merchantDao.updateMerchantStatus(merchantId, status.name(), updatedBy);

        return new MerchantResponse(
                existing.id(), existing.address(), existing.kycStatus(), status, existing.tier(),
                existing.userId(), existing.userFirstname(), existing.userLastname(),
                existing.userEmail(), existing.userPhone(), existing.userStatus(),
                existing.createdAt(), OffsetDateTime.now()
        );
    }

    @Transactional
    public void deleteMerchant(Long merchantId) {
        if (!merchantDao.exists(merchantId)) {
            throw new NotFoundException("Merchant not found");
        }
        Long deletedBy = SecurityUtil.findCurrentUserId().orElse(null);
        merchantDao.softDelete(merchantId, deletedBy);
    }

    public MerchantResponse getMerchantById(Long merchantId) {
        return merchantDao.findById(merchantId)
                .orElseThrow(() -> new NotFoundException("Merchant not found"));
    }

    public Page<MerchantResponse> getAllMerchants(int page, int size, String sortField, Sort.Direction direction) {
        return queryPage(merchantDao.getSelectAllSql(), new MapSqlParameterSource(), page, size, sortField, direction);
    }

    private Page<MerchantResponse> queryPage(String baseSql, MapSqlParameterSource params, int page, int size, String sort, Sort.Direction dir) {
        String safeSort = validateSortField(sort);
        int offset = (page - 1) * size;
        params.addValue("limit", size).addValue("offset", offset);

        long[] total = {0};
        List<MerchantResponse> results = merchantDao.findWithPage(baseSql, params, safeSort, dir.name(), total);
        return new PageImpl<>(results, PageRequest.of(page - 1, size), total[0]);
    }

    private MerchantResponse buildUpdateResponse(MerchantResponse e, String addr, KycStatus kyc, MerchantStatus stat, MerchantTier tier) {
        return new MerchantResponse(e.id(), addr, kyc, stat, tier, e.userId(), e.userFirstname(),
                e.userLastname(), e.userEmail(), e.userPhone(), e.userStatus(), e.createdAt(), OffsetDateTime.now());
    }

    private String validateSortField(String sortField) {
        if (!ALLOWED_SORT_FIELDS.contains(sortField)) {
            throw new BadRequestException("Invalid sort field: " + sortField);
        }
        return sortField;
    }
}