package com.eastwind.EACAfterSaleMgr.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

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
    private LocalDateTime createdDate;

    /**
     * 创建人
     */
    @CreatedBy
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User createdBy;

    /**
     * 修改时间
     */
    @LastModifiedDate
    private LocalDateTime lastModifiedDate;

    /**
     * 修改人
     */
    @LastModifiedBy
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User lastModifiedBy;
}
