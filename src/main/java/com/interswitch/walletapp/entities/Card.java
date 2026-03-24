package com.interswitch.walletapp.entities;

import com.interswitch.walletapp.entities.audit.FullAudit;
import com.interswitch.walletapp.models.enums.CardScheme;
import com.interswitch.walletapp.models.enums.CardStatus;
import com.interswitch.walletapp.models.enums.CardType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cards")
public class Card extends FullAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 19)
    private String cardNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 50)
    private CardType cardType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CardScheme scheme;

    @Column(nullable = false)
    private Short expiryMonth;

    @Column(nullable = false)
    private Short expiryYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_status", nullable = false, length = 50)
    private CardStatus cardStatus;

    public Card(Long id) {
        this.id = id;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;

        Class<?> oEffectiveClass = o instanceof HibernateProxy hibernateProxy
                ? hibernateProxy.getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy hibernateProxy
                ? hibernateProxy.getHibernateLazyInitializer().getPersistentClass()
                : this.getClass();

        if (thisEffectiveClass != oEffectiveClass) return false;
        Card that = (Card) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy hibernateProxy
                ? hibernateProxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Card{" +
                "id='" + id + '\'' +
                ", cardNumber='" + cardNumber + '\'' +
                ", cardType=" + cardType +
                ", scheme=" + scheme +
                ", cardStatus=" + cardStatus +
                '}';
    }
}
