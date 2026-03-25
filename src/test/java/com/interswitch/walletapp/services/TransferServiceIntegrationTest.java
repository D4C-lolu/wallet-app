package com.interswitch.walletapp.services;


import com.interswitch.walletapp.base.BaseIntegrationTest;
import com.interswitch.walletapp.dao.AccountDao;
import com.interswitch.walletapp.entities.User;
import com.interswitch.walletapp.exceptions.BadRequestException;
import com.interswitch.walletapp.exceptions.ConflictException;
import com.interswitch.walletapp.exceptions.ForbiddenException;
import com.interswitch.walletapp.exceptions.NotFoundException;
import com.interswitch.walletapp.models.enums.*;
import com.interswitch.walletapp.models.request.*;
import com.interswitch.walletapp.models.response.TransferResponse;
import com.interswitch.walletapp.repositories.UserRepository;
import com.interswitch.walletapp.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Transfer Service Integration Tests")
public class TransferServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    @BeforeEach
    void setupSecurityContext() {
        User superAdmin = userRepository.findByEmail("demo.merchant@verveguard.com").orElseThrow();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(superAdmin), null, List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private TransferRequest buildTransferRequest(String reference) {
        return new TransferRequest(
                reference,
                1L,
                2L,
                new BigDecimal("1000.00"),
                "NGN",
                "Test transfer",
                "4111111111111111"
        );
    }

    @Test
    @DisplayName("should transfer successfully")
    void shouldTransferSuccessfully() {
        TransferRequest request = buildTransferRequest("REF-001");

        TransferResponse response = transferService.transferForSelf(request);

        assertThat(response.id()).isNotNull().isPositive();
        assertThat(response.reference()).isEqualTo(request.reference());
        assertThat(response.fromAccountId()).isEqualTo(request.fromAccountId());
        assertThat(response.toAccountId()).isEqualTo(request.toAccountId());
        assertThat(response.amount()).isEqualByComparingTo(request.amount());
        assertThat(response.transferStatus()).isEqualTo(TransferStatus.SUCCESS);
    }

    @Test
    @DisplayName("should debit and credit accounts correctly")
    void shouldDebitAndCreditAccountsCorrectly() {
        BigDecimal amount = new BigDecimal("1000.00");
        TransferRequest request = buildTransferRequest("REF-002");

        BigDecimal fromBalanceBefore = getAccountBalance(1L);
        BigDecimal toBalanceBefore = getAccountBalance(2L);

        transferService.transferForSelf(request);

        BigDecimal fromBalanceAfter = getAccountBalance(1L);
        BigDecimal toBalanceAfter = getAccountBalance(2L);

        assertThat(fromBalanceAfter).isEqualByComparingTo(fromBalanceBefore.subtract(amount));
        assertThat(toBalanceAfter).isEqualByComparingTo(toBalanceBefore.add(amount));
    }

    @Test
    @DisplayName("should fail transfer with duplicate reference")
    void shouldFailTransferWithDuplicateReference() {
        TransferRequest request = buildTransferRequest("REF-003");
        transferService.transferForSelf(request);

        assertThatThrownBy(() -> transferService.transferForSelf(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Transfer reference already exists");
    }

    @Test
    @DisplayName("should fail transfer with insufficient funds")
    void shouldFailTransferWithInsufficientFunds() {
        TransferRequest request = new TransferRequest(
                "REF-004",
                1L,
                2L,
                new BigDecimal("999999999999.00"),
                "NGN",
                "Test transfer",
                "4111111111111111"
        );

        assertThatThrownBy(() -> transferService.transferForSelf(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Insufficient funds");
    }

    @Test
    @DisplayName("should fail transfer with currency mismatch")
    void shouldFailTransferWithCurrencyMismatch() {

        User superAdmin = userRepository.findByEmail("demo.merchant@verveguard.com").orElseThrow();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(superAdmin), null, List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        TransferRequest request = new TransferRequest(
                "REF-005",
                1L,
                2L,
                new BigDecimal("1000.00"),
                "USD",
                "Test transfer",
                "4111111111111111"
        );

        assertThatThrownBy(() -> transferService.transferForSelf(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("should transfer for self successfully as merchant")
    void shouldTransferForSelfSuccessfully() {
        User merchantUser = userRepository.findByEmail("demo.merchant@verveguard.com").orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(merchantUser), null, List.of())
        );

        TransferRequest request = buildTransferRequest("REF-006");

        TransferResponse response = transferService.transferForSelf(request);

        assertThat(response.transferStatus()).isEqualTo(TransferStatus.SUCCESS);
    }

    @Test
    @DisplayName("should fail transfer for self when account does not belong to merchant")
    void shouldFailTransferForSelfWhenAccountDoesNotBelongToMerchant() {
        User merchantUser = userRepository.findByEmail("testmerchant@verveguard.com").orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(merchantUser), null, List.of())
        );

        TransferRequest request = buildTransferRequest("REF-007");

        assertThatThrownBy(() -> transferService.transferForSelf(request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Account does not belong to your merchant");
    }

    @Test
    @DisplayName("should get transfer by id successfully")
    void shouldGetTransferByIdSuccessfully() {
        TransferResponse created = transferService.transferForSelf(buildTransferRequest("REF-008"));

        TransferResponse response = transferService.getTransferById(created.id());

        assertThat(response.id()).isEqualTo(created.id());
        assertThat(response.reference()).isEqualTo(created.reference());
    }

    @Test
    @DisplayName("should fail get transfer with non existent id")
    void shouldFailGetTransferWithNonExistentId() {
        assertThatThrownBy(() -> transferService.getTransferById(Long.MAX_VALUE))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Transfer not found");
    }

    @Test
    @DisplayName("should get transfers by account paginated")
    void shouldGetTransfersByAccountPaginated() {
        transferService.transferForSelf(buildTransferRequest("REF-009"));

        Page<TransferResponse> page = transferService.getTransfersByAccount(1L, 1, 10);

        assertThat(page).isNotNull();
        assertThat(page.getContent()).isNotEmpty();
    }

    private BigDecimal getAccountBalance(Long accountId) {
        return namedJdbc.queryForObject(
                "SELECT balance FROM accounts WHERE id = :id",
                new MapSqlParameterSource("id", accountId),
                BigDecimal.class
        );
    }

}