package com.interswitch.walletapp.entities.audit;

import com.interswitch.walletapp.util.SecurityUtil;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Getter
@Setter
@NoArgsConstructor
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class FullAudit {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    protected OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    protected OffsetDateTime updatedAt;

    @CreatedBy
    @Column(updatable = false, length = 26)
    protected Long createdBy;

    @LastModifiedBy
    @Column(length = 26)
    protected Long updatedBy;

    @Column
    protected OffsetDateTime deletedAt;

    @Column(length = 26)
    protected Long deletedBy;

    public boolean isNotDeleted() {
        return deletedAt == null;
    }

    public void softDelete() {
        this.deletedAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (this.deletedBy == null) {
            this.deletedBy = SecurityUtil.getCurrentUserId();
        }
    }
}


