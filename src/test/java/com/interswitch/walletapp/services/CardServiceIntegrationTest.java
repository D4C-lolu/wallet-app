package com.interswitch.walletapp.services;

import com.interswitch.walletapp.base.BaseIntegrationTest;
import com.interswitch.walletapp.entities.User;
import com.interswitch.walletapp.exceptions.BadRequestException;
import com.interswitch.walletapp.exceptions.ConflictException;
import com.interswitch.walletapp.exceptions.ForbiddenException;
import com.interswitch.walletapp.exceptions.NotFoundException;
import com.interswitch.walletapp.models.enums.*;
import com.interswitch.walletapp.models.request.CreateCardRequest;
import com.interswitch.walletapp.models.request.CreateMyCardRequest;
import com.interswitch.walletapp.models.response.CardResponse;
import com.interswitch.walletapp.repositories.AccountRepository;
import com.interswitch.walletapp.repositories.CardRepository;
import com.interswitch.walletapp.repositories.UserRepository;
import com.interswitch.walletapp.security.UserPrincipal;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Card Service Integration Tests")
public class CardServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CardService cardService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CardRepository cardRepository;

    private Long demoAccountId;
    private Long demoCardId;

    @BeforeEach
    void setUp() {
        User superAdmin = userRepository.findByEmail("superadmin@verveguard.com").orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(superAdmin), null, List.of())
        );

        demoAccountId = accountRepository.findByAccountNumber("0000000000")
                .orElseThrow().getId();

        demoCardId = cardRepository.findByCardHash(DigestUtils.sha256Hex("4011111111111111"))
                .orElseThrow().getId();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private CreateCardRequest buildCreateRequest(String cardNumber) {
        return new CreateCardRequest(
                demoAccountId,
                cardNumber,
                CardType.VIRTUAL,
                CardScheme.VISA,
                12, 3028
        );
    }

    @Test
    @DisplayName("should create card successfully")
    void shouldCreateCardSuccessfully() {
        CreateCardRequest request = buildCreateRequest("4111111111111111");

        CardResponse response = cardService.createCard(request);

        assertThat(response.id()).isNotNull().isPositive();
        assertThat(response.accountId()).isEqualTo(demoAccountId);
        assertThat(response.cardType()).isEqualTo(request.cardType());
        assertThat(response.scheme()).isEqualTo(request.scheme());
        assertThat(response.expiryMonth()).isEqualTo(request.expiryMonth());
        assertThat(response.expiryYear()).isEqualTo(request.expiryYear());
        assertThat(response.cardStatus()).isEqualTo(CardStatus.ACTIVE);
        assertThat(response.cardNumber()).contains("****");
    }

    @Test
    @DisplayName("should fail create card with duplicate card number")
    void shouldFailCreateCardWithDuplicateCardNumber() {
        CreateCardRequest request = buildCreateRequest("4222222222222222");
        cardService.createCard(request);

        assertThatThrownBy(() -> cardService.createCard(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Card already exists");
    }

    @Test
    @DisplayName("should fail create card with expired expiry date")
    void shouldFailCreateCardWithExpiredExpiryDate() {
        //noinspection ConstantConditions
        CreateCardRequest request = new CreateCardRequest(
                demoAccountId,
                "4333333333333333",
                CardType.VIRTUAL,
                CardScheme.VISA,
                1, 2020
        );

        assertThatThrownBy(() -> cardService.createCard(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Card expiry date is in the past");
    }

    @Test
    @DisplayName("should fail create card when account not found")
    void shouldFailCreateCardWhenAccountNotFound() {
        CreateCardRequest request = new CreateCardRequest(
                Long.MAX_VALUE,
                "4444444444444444",
                CardType.VIRTUAL,
                CardScheme.VISA,
                12, 3028
        );

        assertThatThrownBy(() -> cardService.createCard(request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Account not found");
    }

    @Test
    @DisplayName("should get card by id successfully")
    void shouldGetCardByIdSuccessfully() {
        CardResponse response = cardService.getCardById(demoCardId);

        assertThat(response.id()).isEqualTo(demoCardId);
        assertThat(response.accountId()).isEqualTo(demoAccountId);
        assertThat(response.cardStatus()).isEqualTo(CardStatus.ACTIVE);
    }

    @Test
    @DisplayName("should fail get card with non existent id")
    void shouldFailGetCardWithNonExistentId() {
        assertThatThrownBy(() -> cardService.getCardById(Long.MAX_VALUE))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Card not found");
    }

    @Test
    @DisplayName("should get cards by account paginated")
    void shouldGetCardsByAccountPaginated() {
        Page<CardResponse> page = cardService.getCardsByAccount(
                demoAccountId, 1, 10, "created_at", Sort.Direction.DESC
        );

        assertThat(page).isNotNull();
        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent().getFirst().accountId()).isEqualTo(demoAccountId);
    }

    @Test
    @DisplayName("should update card status successfully")
    void shouldUpdateCardStatusSuccessfully() {
        CardResponse created = cardService.createCard(buildCreateRequest("4555555555555555"));

        CardResponse response = cardService.updateCardStatus(created.id(), CardStatus.BLOCKED);

        assertThat(response.cardStatus()).isEqualTo(CardStatus.BLOCKED);
    }

    @Test
    @DisplayName("should block card successfully")
    void shouldBlockCardSuccessfully() {
        CardResponse created = cardService.createCard(buildCreateRequest("4666666666666666"));

        assertThatNoException().isThrownBy(() -> cardService.blockCard(created.id()));

        assertThat(cardService.getCardById(created.id()).cardStatus()).isEqualTo(CardStatus.BLOCKED);
    }

    @Test
    @DisplayName("should block card for self successfully")
    void shouldBlockCardForSelfSuccessfully() {
        User merchantUser = userRepository.findByEmail("demo.merchant@verveguard.com").orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(merchantUser), null, List.of())
        );

        assertThatNoException().isThrownBy(() -> cardService.blockCardForSelf(demoCardId));

        assertThat(cardService.getCardById(demoCardId).cardStatus()).isEqualTo(CardStatus.BLOCKED);
    }

    @Test
    @DisplayName("should fail block card for self when card does not belong to merchant")
    void shouldFailBlockCardForSelfWhenCardDoesNotBelongToMerchant() {
        User adminUser = userRepository.findByEmail("testadmin@verveguard.com").orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(adminUser), null, List.of())
        );

        assertThatThrownBy(() -> cardService.blockCardForSelf(demoCardId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Card does not belong to your merchant");
    }

    @Test
    @DisplayName("should create card for self successfully")
    void shouldCreateCardForSelfSuccessfully() {
        User merchantUser = userRepository.findByEmail("demo.merchant@verveguard.com").orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(merchantUser), null, List.of())
        );

        CreateMyCardRequest request = new CreateMyCardRequest(
                demoAccountId,
                "4777777777777777",
                CardType.VIRTUAL,
                CardScheme.VISA,
                12, 3028
        );

        CardResponse response = cardService.createCardForSelf(request);

        assertThat(response.id()).isNotNull().isPositive();
        assertThat(response.accountId()).isEqualTo(demoAccountId);
        assertThat(response.cardStatus()).isEqualTo(CardStatus.ACTIVE);
    }

    @Test
    @DisplayName("should fail create card for self when account does not belong to merchant")
    void shouldFailCreateCardForSelfWhenAccountDoesNotBelongToMerchant() {
        User merchantUser = userRepository.findByEmail("testmerchant2@verveguard.com").orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(merchantUser), null, List.of())
        );

        CreateMyCardRequest request = new CreateMyCardRequest(
                demoAccountId,
                "4888888888888888",
                CardType.VIRTUAL,
                CardScheme.VISA,
                12, 3028
        );

        assertThatThrownBy(() -> cardService.createCardForSelf(request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Account does not belong to you");
    }

    @Test
    @DisplayName("should soft delete card successfully")
    void shouldSoftDeleteCardSuccessfully() {
        CardResponse created = cardService.createCard(buildCreateRequest("4999999999999999"));

        assertThatNoException().isThrownBy(() -> cardService.deleteCard(created.id()));
    }

    @Test
    @DisplayName("should not find soft deleted card")
    void shouldNotFindSoftDeletedCard() {
        CardResponse created = cardService.createCard(buildCreateRequest("4000000000000000"));
        cardService.deleteCard(created.id());

        assertThatThrownBy(() -> cardService.getCardById(created.id()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Card not found");
    }
}