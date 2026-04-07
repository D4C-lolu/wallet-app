package com.interswitch.walletapp.entities.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Getter
@Setter
@NoArgsConstructor
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class TimestampAudit {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    protected OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    protected OffsetDateTime updatedAt;

    @Column
    protected OffsetDateTime deletedAt;

    public boolean isNotDeleted() {
        return deletedAt == null;
    }

    public void softDelete() {
        this.deletedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}