package pers.eastwind.billmanager.attach.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import pers.eastwind.billmanager.security.model.AuditEntity;

@EqualsAndHashCode(callSuper = true)
@Entity
@Data
@Table(name = "attachment")
public class Attach extends AuditEntity {
    @Id
    @Column(insertable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    @Enumerated
    private AttachType type = AttachType.OTHER;
    private String relativePath;
    @Enumerated
    private AttachStatus status = AttachStatus.TEMP;
}
