package pers.eastwind.billmanager.model.entity;

import jakarta.persistence.*;
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

    /**
     * 创建时间
     */
    @CreatedDate
    private Instant createdDate;

    /**
     * 创建人 ID
     */
    @CreatedBy
    private Integer createdById;

    /**
     * 修改时间
     */
    @LastModifiedDate
    private Instant lastModifiedDate;

    /**
     * 修改人 ID
     */
    @LastModifiedBy
    private Integer lastModifiedById;
}
