package com.interswitch.walletapp.services;

import com.interswitch.walletapp.base.BaseIntegrationTest;
import com.interswitch.walletapp.entities.Merchant;
import com.interswitch.walletapp.entities.User;
import com.interswitch.walletapp.exceptions.BadRequestException;
import com.interswitch.walletapp.exceptions.ConflictException;
import com.interswitch.walletapp.exceptions.NotFoundException;
import com.interswitch.walletapp.models.enums.KycStatus;
import com.interswitch.walletapp.models.enums.MerchantStatus;
import com.interswitch.walletapp.models.enums.MerchantTier;
import com.interswitch.walletapp.models.request.CreateMerchantRequest;
import com.interswitch.walletapp.models.request.MerchantSignupRequest;
import com.interswitch.walletapp.models.request.UpdateMerchantRequest;
import com.interswitch.walletapp.models.response.MerchantResponse;
import com.interswitch.walletapp.repositories.MerchantRepository;
import com.interswitch.walletapp.repositories.UserRepository;
import com.interswitch.walletapp.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.data.domain.Sort.Direction.DESC;

@DisplayName("Merchant Service Integration Tests")
public class MerchantServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private UserRepository userRepository;

    private Long testMerchantUserId;
    private Long superAdminId;

    @BeforeEach
    void setUp() {
        User superAdmin = userRepository.findByEmail("superadmin@verveguard.com").orElseThrow();
        superAdminId = superAdmin.getId();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(superAdmin), null, List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        testMerchantUserId = userRepository.findByEmail("testmerchant3@verveguard.com")
                .orElseThrow().getId();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private CreateMerchantRequest buildCreateRequest(Long userId) {
        return new CreateMerchantRequest(userId, "123 Test Street");
    }

    @Test
    @DisplayName("should create merchant successfully")
    void shouldCreateMerchantSuccessfully() {
        MerchantResponse response = merchantService.createMerchant(buildCreateRequest(testMerchantUserId));

        assertThat(response.id()).isNotNull().isPositive();
        assertThat(response.address()).isEqualTo("123 Test Street");
        assertThat(response.kycStatus()).isEqualTo(KycStatus.PENDING);
        assertThat(response.merchantStatus()).isEqualTo(MerchantStatus.INACTIVE);
        assertThat(response.tier()).isEqualTo(MerchantTier.TIER_1);
    }

    @Test
    @DisplayName("should populate createdBy when creating merchant")
    void shouldPopulateCreatedByWhenCreatingMerchant() {
        MerchantResponse response = merchantService.createMerchant(buildCreateRequest(testMerchantUserId));
        Merchant saved = merchantRepository.findById(response.id()).orElseThrow();

        assertThat(saved.getCreatedBy()).isEqualTo(superAdminId);
    }

    @Test
    @DisplayName("should fail create merchant when user already has merchant account")
    void shouldFailCreateMerchantWhenUserAlreadyHasMerchantAccount() {
        merchantService.createMerchant(buildCreateRequest(testMerchantUserId));

        assertThatThrownBy(() -> merchantService.createMerchant(buildCreateRequest(testMerchantUserId)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("User already has a merchant account");
    }

    @Test
    @DisplayName("should fail create merchant with non existent user")
    void shouldFailCreateMerchantWithNonExistentUser() {
        assertThatThrownBy(() -> merchantService.createMerchant(buildCreateRequest(Long.MAX_VALUE)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    @DisplayName("should get merchant by id successfully")
    void shouldGetMerchantByIdSuccessfully() {
        MerchantResponse created = merchantService.createMerchant(buildCreateRequest(testMerchantUserId));

        MerchantResponse response = merchantService.getMerchantById(created.id());

        assertThat(response.id()).isEqualTo(created.id());
        assertThat(response.address()).isEqualTo(created.address());
    }

    @Test
    @DisplayName("should fail get merchant with non existent id")
    void shouldFailGetMerchantWithNonExistentId() {
        assertThatThrownBy(() -> merchantService.getMerchantById(Long.MAX_VALUE))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Merchant not found");
    }

    @Test
    @DisplayName("should get all merchants paginated")
    void shouldGetAllMerchantsPaginated() {
        merchantService.createMerchant(buildCreateRequest(testMerchantUserId));

        Page<MerchantResponse> page = merchantService.getAllMerchants(1, 10, "created_at", DESC);

        assertThat(page).isNotNull();
        assertThat(page.getContent()).isNotEmpty();
    }

    @Test
    @DisplayName("should update merchant successfully")
    void shouldUpdateMerchantSuccessfully() {
        MerchantResponse created = merchantService.createMerchant(buildCreateRequest(testMerchantUserId));
        UpdateMerchantRequest request = new UpdateMerchantRequest("456 Updated Street");

        MerchantResponse response = merchantService.updateMerchant(created.id(), request);

        assertThat(response.address()).isEqualTo(request.address());
    }

    @Test
    @DisplayName("should update kyc status to approved and activate merchant")
    void shouldUpdateKycStatusToApprovedAndActivateMerchant() {
        MerchantResponse created = merchantService.createMerchant(buildCreateRequest(testMerchantUserId));

        MerchantResponse response = merchantService.updateKycStatus(created.id(), KycStatus.APPROVED);

        assertThat(response.kycStatus()).isEqualTo(KycStatus.APPROVED);
        assertThat(response.merchantStatus()).isEqualTo(MerchantStatus.ACTIVE);
    }

    @Test
    @DisplayName("should update kyc status to rejected and deactivate merchant")
    void shouldUpdateKycStatusToRejectedAndDeactivateMerchant() {
        MerchantResponse created = merchantService.createMerchant(buildCreateRequest(testMerchantUserId));

        MerchantResponse response = merchantService.updateKycStatus(created.id(), KycStatus.REJECTED);

        assertThat(response.kycStatus()).isEqualTo(KycStatus.REJECTED);
        assertThat(response.merchantStatus()).isEqualTo(MerchantStatus.INACTIVE);
    }

    @Test
    @DisplayName("should fail update kyc status when already approved")
    void shouldFailUpdateKycStatusWhenAlreadyApproved() {
        MerchantResponse created = merchantService.createMerchant(buildCreateRequest(testMerchantUserId));
        merchantService.updateKycStatus(created.id(), KycStatus.APPROVED);

        assertThatThrownBy(() -> merchantService.updateKycStatus(created.id(), KycStatus.REJECTED))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Merchant KYC is already approved");
    }

    @Test
    @DisplayName("should upgrade tier successfully")
    void shouldUpgradeTierSuccessfully() {
        MerchantResponse created = merchantService.createMerchant(buildCreateRequest(testMerchantUserId));
        merchantService.updateKycStatus(created.id(), KycStatus.APPROVED);

        MerchantResponse response = merchantService.upgradeTier(created.id());

        assertThat(response.tier()).isEqualTo(MerchantTier.TIER_2);
    }

    @Test
    @DisplayName("should fail upgrade tier when not kyc approved")
    void shouldFailUpgradeTierWhenNotKycApproved() {
        MerchantResponse created = merchantService.createMerchant(buildCreateRequest(testMerchantUserId));

        assertThatThrownBy(() -> merchantService.upgradeTier(created.id()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Merchant must be KYC approved before upgrading tier");
    }

    @Test
    @DisplayName("should fail upgrade tier when already on highest tier")
    void shouldFailUpgradeTierWhenAlreadyOnHighestTier() {
        MerchantResponse created = merchantService.createMerchant(buildCreateRequest(testMerchantUserId));
        merchantService.updateKycStatus(created.id(), KycStatus.APPROVED);
        merchantService.upgradeTier(created.id());
        merchantService.upgradeTier(created.id());

        assertThatThrownBy(() -> merchantService.upgradeTier(created.id()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Merchant is already on the highest tier");
    }

    @Test
    @DisplayName("should downgrade tier successfully")
    void shouldDowngradeTierSuccessfully() {
        MerchantResponse created = merchantService.createMerchant(buildCreateRequest(testMerchantUserId));
        merchantService.updateKycStatus(created.id(), KycStatus.APPROVED);
        merchantService.upgradeTier(created.id());

        MerchantResponse response = merchantService.downgradeTier(created.id());

        assertThat(response.tier()).isEqualTo(MerchantTier.TIER_1);
    }

    @Test
    @DisplayName("should fail downgrade tier when already on lowest tier")
    void shouldFailDowngradeTierWhenAlreadyOnLowestTier() {
        MerchantResponse created = merchantService.createMerchant(buildCreateRequest(testMerchantUserId));

        assertThatThrownBy(() -> merchantService.downgradeTier(created.id()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Merchant is already on the lowest tier");
    }

    @Test
    @DisplayName("should update merchant status successfully")
    void shouldUpdateMerchantStatusSuccessfully() {
        MerchantResponse created = merchantService.createMerchant(buildCreateRequest(testMerchantUserId));
        merchantService.updateKycStatus(created.id(), KycStatus.APPROVED);

        MerchantResponse response = merchantService.updateMerchantStatus(created.id(), MerchantStatus.SUSPENDED);

        assertThat(response.merchantStatus()).isEqualTo(MerchantStatus.SUSPENDED);
    }

    @Test
    @DisplayName("should fail activate merchant when not kyc approved")
    void shouldFailActivateMerchantWhenNotKycApproved() {
        MerchantResponse created = merchantService.createMerchant(buildCreateRequest(testMerchantUserId));

        assertThatThrownBy(() -> merchantService.updateMerchantStatus(created.id(), MerchantStatus.ACTIVE))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Merchant must be KYC approved before activation");
    }

    @Test
    @DisplayName("should soft delete merchant successfully")
    void shouldSoftDeleteMerchantSuccessfully() {
        MerchantResponse created = merchantService.createMerchant(buildCreateRequest(testMerchantUserId));

        merchantService.deleteMerchant(created.id());

        Merchant deleted = merchantRepository.findById(created.id()).orElseThrow();
        assertThat(deleted.isNotDeleted()).isFalse();
        assertThat(deleted.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("should not find soft deleted merchant")
    void shouldNotFindSoftDeletedMerchant() {
        MerchantResponse created = merchantService.createMerchant(buildCreateRequest(testMerchantUserId));
        merchantService.deleteMerchant(created.id());

        assertThatThrownBy(() -> merchantService.getMerchantById(created.id()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Merchant not found");
    }

    @Test
    @DisplayName("should register new user as merchant successfully")
    void shouldRegisterNewUserAsMerchantSuccessfully() {
        MerchantSignupRequest request = new MerchantSignupRequest(
                "New", "Merchant", null,
                "newmerchant@test.com", "88888888888",
                "Admin123!", "789 New Street"
        );

        MerchantResponse response = merchantService.registerNewUserAsMerchant(request);

        assertThat(response.id()).isNotNull().isPositive();
        assertThat(response.kycStatus()).isEqualTo(KycStatus.PENDING);
        assertThat(response.merchantStatus()).isEqualTo(MerchantStatus.INACTIVE);
        assertThat(response.tier()).isEqualTo(MerchantTier.TIER_1);
    }

    @Test
    @DisplayName("should fail register new user as merchant with duplicate email")
    void shouldFailRegisterNewUserAsMerchantWithDuplicateEmail() {
        MerchantSignupRequest request = new MerchantSignupRequest(
                "New", "Merchant", null,
                "testmerchant@verveguard.com", "88888888888",
                "Admin123!", "789 New Street"
        );

        assertThatThrownBy(() -> merchantService.registerNewUserAsMerchant(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email already in use");
    }

    @Test
    @DisplayName("should self register existing user as merchant successfully")
    void shouldSelfRegisterExistingUserAsMerchantSuccessfully() {
        User merchantUser = userRepository.findByEmail("testmerchant3@verveguard.com").orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(merchantUser), null, List.of())
        );

        MerchantResponse response = merchantService.selfRegisterExistingUser("123 Self Street");

        assertThat(response.id()).isNotNull().isPositive();
        assertThat(response.kycStatus()).isEqualTo(KycStatus.PENDING);
        assertThat(response.merchantStatus()).isEqualTo(MerchantStatus.INACTIVE);
    }

    @Test
    @DisplayName("should fail self register when user already has merchant account")
    void shouldFailSelfRegisterWhenUserAlreadyHasMerchantAccount() {
        User merchantUser = userRepository.findByEmail("testmerchant3@verveguard.com").orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(merchantUser), null, List.of())
        );

        merchantService.selfRegisterExistingUser("123 Self Street");

        assertThatThrownBy(() -> merchantService.selfRegisterExistingUser("123 Self Street"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("User already has a merchant account");
    }
}