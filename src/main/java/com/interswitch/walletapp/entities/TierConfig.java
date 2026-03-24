package com.interswitch.walletapp.entities;

import com.interswitch.walletapp.entities.audit.MutableAudit;
import com.interswitch.walletapp.models.enums.MerchantTier;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.util.Objects;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tier_config")
public class TierConfig extends MutableAudit {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 50)
    private MerchantTier tier;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal dailyTransactionLimit;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal singleTransactionLimit;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal monthlyTransactionLimit;

    @Column(nullable = false)
    private Integer maxCards;

    @Column(nullable = false)
    private Integer maxAccounts;

    public TierConfig(Long id) {
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
        TierConfig that = (TierConfig) o;
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
        return "TierConfig{" +
                "id='" + id + '\'' +
                ", tier=" + tier +
                ", dailyTransactionLimit=" + dailyTransactionLimit +
                ", singleTransactionLimit=" + singleTransactionLimit +
                ", monthlyTransactionLimit=" + monthlyTransactionLimit +
                '}';
    }
}