package pers.eastwind.billmanager.security.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 账户
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "sys_user", indexes = @Index(columnList = "username"))
public class Account extends AuditEntity {
    @Id
    @Column(insertable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(unique = true)
    private String username;
    private String password;
    private boolean disabled;
}
