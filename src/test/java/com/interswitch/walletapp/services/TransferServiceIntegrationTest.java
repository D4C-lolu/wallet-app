package com.interswitch.walletapp.services;


import com.interswitch.walletapp.base.BaseIntegrationTest;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Transfer Service Integration Tests")
public class TransferServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    private static final AtomicInteger IP_COUNTER = new AtomicInteger(1);

    @BeforeEach
    void setupSecurityContext() {
        User superAdmin = userRepository.findByEmail("superadmin@verveguard.com").orElseThrow();
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
                "01JACCTS0000000000000001AA",
                "01JACCTS0000000000000002BB",
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

        TransferResponse response = transferService.transfer(request, uniqueIp());

        assertThat(response.id()).isNotBlank();
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
        TransferRequest request = new TransferRequest(
                "REF-002",
                "01JACCTS0000000000000001AA",
                "01JACCTS0000000000000002BB",
                amount, "NGN", "Test transfer", "4111111111111111"
        );

        BigDecimal fromBalanceBefore = getAccountBalance("01JACCTS0000000000000001AA");
        BigDecimal toBalanceBefore   = getAccountBalance("01JACCTS0000000000000002BB");

        transferService.transfer(request, uniqueIp());

        BigDecimal fromBalanceAfter = getAccountBalance("01JACCTS0000000000000001AA");
        BigDecimal toBalanceAfter   = getAccountBalance("01JACCTS0000000000000002BB");

        assertThat(fromBalanceAfter).isEqualByComparingTo(fromBalanceBefore.subtract(amount));
        assertThat(toBalanceAfter).isEqualByComparingTo(toBalanceBefore.add(amount));
    }

    @Test
    @DisplayName("should fail transfer with duplicate reference")
    void shouldFailTransferWithDuplicateReference() {
        TransferRequest request = buildTransferRequest("REF-003");
        transferService.transfer(request, uniqueIp());

        assertThatThrownBy(() -> transferService.transfer(request, uniqueIp()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Transfer reference already exists");
    }

    @Test
    @DisplayName("should fail transfer with insufficient funds")
    void shouldFailTransferWithInsufficientFunds() {
        TransferRequest request = new TransferRequest(
                "REF-004",
                "01JACCTS0000000000000001AA",
                "01JACCTS0000000000000002BB",
                new BigDecimal("999999999999.00"),
                "NGN", "Test transfer", "4111111111111111"
        );

        assertThatThrownBy(() -> transferService.transfer(request, uniqueIp()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Insufficient funds");
    }

    @Test
    @DisplayName("should fail transfer with currency mismatch")
    void shouldFailTransferWithCurrencyMismatch() {
        TransferRequest request = new TransferRequest(
                "REF-005",
                "01JACCTS0000000000000001AA",
                "01JACCTS0000000000000002BB",
                new BigDecimal("1000.00"),
                "USD", "Test transfer", "4111111111111111"
        );

        assertThatThrownBy(() -> transferService.transfer(request, uniqueIp()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("should fail transfer when blocked by fraud detection")
    void shouldFailTransferWhenBlockedByFraudDetection() {
        TransferRequest request = new TransferRequest(
                "REF-006",
                "01JACCTS0000000000000001AA",
                "01JACCTS0000000000000002BB",
                new BigDecimal("1000.00"),
                "NGN", "Test transfer", "4111111111111111"
        );
        String ip = "10.0.0.99";

        for (int i = 0; i <= 5; i++) {
            try { transferService.transfer(
                new TransferRequest("REF-RATE-" + i, request.fromAccountId(),
                    request.toAccountId(), request.amount(),
                    request.currency(), request.description(), request.cardNumber()),
                ip);
            } catch (Exception ignored) {}
        }

        assertThatThrownBy(() -> transferService.transfer(request, ip))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Transaction blocked due to fraud detection");
    }

    @Test
    @DisplayName("should transfer for self successfully as merchant")
    void shouldTransferForSelfSuccessfully() {
        User merchantUser = userRepository.findByEmail("demo.merchant@verveguard.com").orElseThrow();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(merchantUser), null, List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        TransferRequest request = buildTransferRequest("REF-007");

        TransferResponse response = transferService.transferForSelf(request, uniqueIp());

        assertThat(response.transferStatus()).isEqualTo(TransferStatus.SUCCESS);
    }

    @Test
    @DisplayName("should fail transfer for self when account does not belong to merchant")
    void shouldFailTransferForSelfWhenAccountDoesNotBelongToMerchant() {
        User merchantUser = userRepository.findByEmail("testmerchant@verveguard.com").orElseThrow();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(merchantUser), null, List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        TransferRequest request = buildTransferRequest("REF-008");

        assertThatThrownBy(() -> transferService.transferForSelf(request, uniqueIp()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Account does not belong to your merchant");
    }

    @Test
    @DisplayName("should get transfer by id successfully")
    void shouldGetTransferByIdSuccessfully() {
        TransferResponse created = transferService.transfer(buildTransferRequest("REF-009"), uniqueIp());

        TransferResponse response = transferService.getTransferById(created.id());

        assertThat(response.id()).isEqualTo(created.id());
        assertThat(response.reference()).isEqualTo(created.reference());
    }

    @Test
    @DisplayName("should fail get transfer with non existent id")
    void shouldFailGetTransferWithNonExistentId() {
        assertThatThrownBy(() -> transferService.getTransferById("NONEXISTENT00000000000000"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Transfer not found");
    }

    @Test
    @DisplayName("should get transfers by account paginated")
    void shouldGetTransfersByAccountPaginated() {
        transferService.transfer(buildTransferRequest("REF-010"), uniqueIp());

        Page<TransferResponse> page = transferService.getTransfersByAccount(
                "01JACCTS0000000000000001AA", 1, 10
        );

        assertThat(page).isNotNull();
        assertThat(page.getContent()).isNotEmpty();
    }

    private BigDecimal getAccountBalance(String accountId) {
        return namedJdbc.queryForObject(
                "SELECT balance FROM accounts WHERE id = :id",
                new MapSqlParameterSource("id", accountId),
                BigDecimal.class
        );
    }
}