package pers.eastwind.billmanager.user.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import pers.eastwind.billmanager.security.model.AuditEntity;

/**
 * 用户实体
 */
@Entity
@Table(name = "sys_user",
        indexes = @Index(columnList = "username"))
@Data
@EqualsAndHashCode(callSuper = true)
public class User extends AuditEntity {
    @Id
    @Column(insertable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(unique = true)
    private String username;

    private String password;

    private String name;

    private String phone;

    private String email;

    private boolean disabled;
}
