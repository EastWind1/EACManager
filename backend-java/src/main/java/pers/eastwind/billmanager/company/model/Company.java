package pers.eastwind.billmanager.company.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import pers.eastwind.billmanager.security.model.AuditEntity;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
public class Company extends AuditEntity {
    @Id
    @Column(insertable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;

    private String contactName;

    private String contactPhone;

    private String email;

    private String address;

    private Boolean disabled = false;

}
