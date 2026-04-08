package com.interswitch.walletapp.services;

import com.interswitch.walletapp.annotation.DisableFraudDetection;
import com.interswitch.walletapp.base.BaseIntegrationTest;
import com.interswitch.walletapp.entities.User;
import com.interswitch.walletapp.exceptions.NotFoundException;
import com.interswitch.walletapp.models.enums.TransactionType;
import com.interswitch.walletapp.models.request.TransferRequest;
import com.interswitch.walletapp.models.response.TransactionResponse;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisableFraudDetection
@DisplayName("Transaction Service Integration Tests")
public class TransactionServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private UserRepository userRepository;

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

    private TransferRequest buildTransferRequest() {
        return new TransferRequest(
                "0000000000",
                "1100000001",
                new BigDecimal("1000.00"),
                "NGN",
                "Test transfer",
                "4111111111111111"
        );
    }

    @Test
    @DisplayName("should get transaction by id successfully")
    void shouldGetTransactionByIdSuccessfully() {
        transferService.transferForSelf(buildTransferRequest(),"REF-TXN-001");

        Page<TransactionResponse> page = transactionService.getTransactionsByAccount(1L, 1, 10);
        Long transactionId = page.getContent().getFirst().id();

        TransactionResponse response = transactionService.getTransactionById(transactionId);

        assertThat(response.id()).isEqualTo(transactionId);
        assertThat(response.accountId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("should fail get transaction with non existent id")
    void shouldFailGetTransactionWithNonExistentId() {
        assertThatThrownBy(() -> transactionService.getTransactionById(Long.MAX_VALUE))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Transaction not found");
    }

    @Test
    @DisplayName("should get transactions by account paginated")
    void shouldGetTransactionsByAccountPaginated() {
        transferService.transferForSelf(buildTransferRequest(),"REF-TXN-002");

        Page<TransactionResponse> page = transactionService.getTransactionsByAccount(1L, 1, 10);

        assertThat(page).isNotNull();
        assertThat(page.getContent()).isNotEmpty();
    }

    @Test
    @DisplayName("should record debit transaction on source account")
    void shouldRecordDebitTransactionOnSourceAccount() {
        transferService.transferForSelf(buildTransferRequest(),"REF-TXN-003");

        Page<TransactionResponse> page = transactionService.getTransactionsByAccount(1L, 1, 10);

        assertThat(page.getContent()).anyMatch(t ->
                t.transactionType() == TransactionType.DEBIT &&
                        t.amount().compareTo(new BigDecimal("1000.00")) == 0
        );
    }

    @Test
    @DisplayName("should record credit transaction on destination account")
    void shouldRecordCreditTransactionOnDestinationAccount() {
        transferService.transferForSelf(buildTransferRequest(),"REF-TXN-004");

        Page<TransactionResponse> page = transactionService.getTransactionsByAccount(2L, 1, 10);

        assertThat(page.getContent()).anyMatch(t ->
                t.transactionType() == TransactionType.CREDIT &&
                        t.amount().compareTo(new BigDecimal("1000.00")) == 0
        );
    }

    @Test
    @DisplayName("should get daily debit sum correctly")
    void shouldGetDailyDebitSumCorrectly() {
        transferService.transferForSelf(buildTransferRequest(),"REF-TXN-005");
        transferService.transferForSelf(buildTransferRequest(),"REF-TXN-006");

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startOfDay = now.toLocalDate().atStartOfDay().atOffset(now.getOffset());

        BigDecimal sum = transactionService.getDailyDebitSum(1L, startOfDay, now);

        assertThat(sum).isGreaterThanOrEqualTo(new BigDecimal("2000.00"));
    }

    @Test
    @DisplayName("should get monthly debit sum correctly")
    void shouldGetMonthlyDebitSumCorrectly() {
        transferService.transferForSelf(buildTransferRequest(), "REF-TXN-007");

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startOfMonth = now.toLocalDate().withDayOfMonth(1).atStartOfDay().atOffset(now.getOffset());

        BigDecimal sum = transactionService.getMonthlyDebitSum(1L, startOfMonth, now);

        assertThat(sum).isGreaterThanOrEqualTo(new BigDecimal("1000.00"));
    }
}
