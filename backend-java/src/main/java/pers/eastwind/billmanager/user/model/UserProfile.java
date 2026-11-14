package pers.eastwind.billmanager.user.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import pers.eastwind.billmanager.security.model.AuditEntity;

/**
 * 用户资料，与安全模块的账户 sys_user 通过 user_id 一一对应。
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "user_profile")
public class UserProfile extends AuditEntity {
    @Id
    @Column(name = "user_id")
    private Integer userId;
    private String name;
    private String phone;
    private String email;
}
