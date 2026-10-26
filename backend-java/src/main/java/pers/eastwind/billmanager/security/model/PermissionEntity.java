package pers.eastwind.billmanager.security.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 动作权限类型
 * <p>
 * 不手动预置，由各模块启动时同步至数据库。
 */
@Entity
@Table(name = "sys_permission", uniqueConstraints = @UniqueConstraint(name = "uk_permission_code", columnNames = "code"))
@Data
@EqualsAndHashCode(callSuper = true)
public class PermissionEntity extends AuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 32)
    private String module;

    @Column(nullable = false, length = 64)
    private String moduleName;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false)
    private Integer sortOrder = 0;
}
