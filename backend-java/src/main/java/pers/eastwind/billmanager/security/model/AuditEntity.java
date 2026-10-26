package pers.eastwind.billmanager.security.model;

import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 审计实体
 */
@Data
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class AuditEntity {
    @CreatedDate
    private Instant createdDate;

    @CreatedBy
    private Integer createdById;

    @LastModifiedDate
    private Instant lastModifiedDate;

    @LastModifiedBy
    private Integer lastModifiedById;
}
