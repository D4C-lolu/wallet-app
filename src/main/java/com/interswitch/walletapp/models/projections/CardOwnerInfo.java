package com.interswitch.walletapp.models.projections;

public record CardOwnerInfo(
        Long cardId,
        String cardNumber,
        String cardType,
        String scheme,
        String email
) {
}
