package pers.eastwind.billmanager.security.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色
 */
@Entity
@Table(name = "sys_role", uniqueConstraints = @UniqueConstraint(name = "uk_role_code", columnNames = "code"))
@Data
@EqualsAndHashCode(callSuper = true)
public class Role extends AuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 角色编码，如 ADMIN
     */
    @Column(nullable = false, length = 64)
    private String code;

    /**
     * 角色名称，如 管理员
     */
    @Column(nullable = false, length = 64)
    private String name;

    /**
     * 排序号
     */
    @Column(nullable = false)
    private Integer sortOrder = 0;

    /**
     * 是否禁用
     */
    @Column(nullable = false)
    private boolean disabled;
}
