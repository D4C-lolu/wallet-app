package com.interswitch.walletapp.repositories;

import com.interswitch.walletapp.entities.Card;
import com.interswitch.walletapp.models.enums.CardStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long> {

    Optional<Card> findByCardHash(String cardHash);

    List<Card> findAllByAccountId(Long account_id);

    List<Card> findAllByAccountIdAndDeletedAtIsNull(Long account_id);

    List<Card> findAllByCardStatus(CardStatus cardStatus);

    boolean existsByCardNumber(String cardNumber);

    int countByAccountIdAndDeletedAtIsNull(Long account_id);
}
