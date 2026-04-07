package com.interswitch.walletapp.services;

import com.interswitch.walletapp.base.BaseIntegrationTest;
import com.interswitch.walletapp.entities.User;
import com.interswitch.walletapp.exceptions.BadRequestException;
import com.interswitch.walletapp.exceptions.NotFoundException;
import com.interswitch.walletapp.models.enums.*;
import com.interswitch.walletapp.models.request.*;
import com.interswitch.walletapp.models.response.AccountResponse;
import com.interswitch.walletapp.repositories.AccountRepository;
import com.interswitch.walletapp.repositories.MerchantRepository;
import com.interswitch.walletapp.repositories.UserRepository;
import com.interswitch.walletapp.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Account Service Integration Tests")
public class AccountServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private AccountRepository accountRepository;

    private Long merchantId;
    private Long demoMerchantId;
    private Long pendingMerchantId;
    private Long demoAccountId;

    @BeforeEach
    void setUp() {
        User superAdmin = userRepository.findByEmail("superadmin@verveguard.com").orElseThrow();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(superAdmin), null, List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        demoMerchantId = merchantRepository.findByUserEmail("demo.merchant@verveguard.com")
                .orElseThrow().getId();

        pendingMerchantId = merchantRepository.findByUserEmail("testmerchant2@verveguard.com")
                .orElseThrow().getId();

        demoAccountId = accountRepository.findByAccountNumber("0000000000")
                .orElseThrow().getId();

        merchantId = merchantRepository.findByUserEmail("testadmin@verveguard.com").
                orElseThrow().getId();

    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("should create account successfully")
    void shouldCreateAccountSuccessfully() {
        CreateAccountRequest request = new CreateAccountRequest(
                merchantId, AccountType.WALLET, "NGN"
        );

        AccountResponse response = accountService.createAccount(request);

        assertThat(response.id()).isNotNull().isPositive();
        assertThat(response.merchantId()).isEqualTo(merchantId);
        assertThat(response.accountType()).isEqualTo(request.accountType());
        assertThat(response.currency()).isEqualTo(request.currency());
        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.ledgerBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.accountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.accountNumber()).isNotBlank();
    }

    @Test
    @DisplayName("should fail create account when merchant not found")
    void shouldFailCreateAccountWhenMerchantNotFound() {
        CreateAccountRequest request = new CreateAccountRequest(
                Long.MAX_VALUE, AccountType.WALLET, "NGN"
        );

        assertThatThrownBy(() -> accountService.createAccount(request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Merchant not found");
    }

    @Test
    @DisplayName("should fail create account when merchant not kyc approved")
    void shouldFailCreateAccountWhenMerchantNotKycApproved() {
        CreateAccountRequest request = new CreateAccountRequest(
                pendingMerchantId, AccountType.WALLET, "NGN"
        );

        assertThatThrownBy(() -> accountService.createAccount(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Merchant must be KYC approved to create an account");
    }

    @Test
    @DisplayName("should get account by id successfully")
    void shouldGetAccountByIdSuccessfully() {
        AccountResponse response = accountService.getAccountById(demoAccountId);

        assertThat(response.id()).isEqualTo(demoAccountId);
        assertThat(response.merchantId()).isEqualTo(demoMerchantId);
        assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("500000000.0000"));
    }

    @Test
    @DisplayName("should fail get account with non existent id")
    void shouldFailGetAccountWithNonExistentId() {
        assertThatThrownBy(() -> accountService.getAccountById(Long.MAX_VALUE))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Account not found");
    }

    @Test
    @DisplayName("should get accounts by merchant paginated")
    void shouldGetAccountsByMerchantPaginated() {
        Page<AccountResponse> page = accountService.getAccountsByMerchant(
                demoMerchantId, 1, 10, "created_at", Sort.Direction.DESC
        );

        assertThat(page).isNotNull();
        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent().getFirst().merchantId()).isEqualTo(demoMerchantId);
    }

    @Test
    @DisplayName("should update account status successfully")
    void shouldUpdateAccountStatusSuccessfully() {
        AccountResponse response = accountService.updateAccountStatus(
                demoAccountId, AccountStatus.SUSPENDED
        );

        assertThat(response.accountStatus()).isEqualTo(AccountStatus.SUSPENDED);
    }

    @Test
    @DisplayName("should create account for self successfully")
    void shouldCreateAccountForSelfSuccessfully() {
        User merchantUser = userRepository.findByEmail("testadmin@verveguard.com").orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(merchantUser), null, List.of())
        );

        CreateMyAccountRequest request = new CreateMyAccountRequest(AccountType.WALLET, "NGN");
        AccountResponse response = accountService.createAccountForSelf(request);

        assertThat(response.id()).isNotNull().isPositive();
        assertThat(response.merchantId()).isEqualTo(merchantId);
        assertThat(response.accountType()).isEqualTo(request.accountType());
        assertThat(response.currency()).isEqualTo(request.currency());
    }

    @Test
    @DisplayName("should fail create account for self when max accounts reached")
    void shouldFailCreateAccountForSelfWhenMaxAccountsReached() {
        User merchantUser = userRepository.findByEmail("demo.merchant@verveguard.com").orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(merchantUser), null, List.of())
        );

        CreateMyAccountRequest request = new CreateMyAccountRequest(AccountType.WALLET, "NGN");
        for (int i = 0; i < 3; i++) {
            try { accountService.createAccountForSelf(request); } catch (Exception ignored) {}
        }

        assertThatThrownBy(() -> accountService.createAccountForSelf(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Merchant has reached maximum number of accounts for their tier");
    }

    @Test
    @DisplayName("should soft delete account successfully")
    void shouldSoftDeleteAccountSuccessfully() {
        AccountResponse created = accountService.createAccount(
                new CreateAccountRequest(merchantId, AccountType.ESCROW, "NGN")
        );

        assertThatNoException().isThrownBy(() -> accountService.deleteAccount(created.id()));
    }

    @Test
    @DisplayName("should not find soft deleted account")
    void shouldNotFindSoftDeletedAccount() {
        AccountResponse created = accountService.createAccount(
                new CreateAccountRequest(merchantId, AccountType.ESCROW, "NGN")
        );
        accountService.deleteAccount(created.id());

        assertThatThrownBy(() -> accountService.getAccountById(created.id()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Account not found");
    }
}