package pers.eastwind.billmanager.user.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import pers.eastwind.billmanager.common.model.AuditEntity;

import java.util.Collection;
import java.util.Collections;

/**
 * 用户实体
 */
@Entity
@Table(name = "sys_user",
        indexes = @Index(columnList = "username"))
@Data
@EqualsAndHashCode(callSuper = true)
public class User extends AuditEntity implements UserDetails {

    @Id
    @Column(insertable = false, updatable = false)
    private Integer id;

    /**
     * 用户名
     */
    @Column(unique = true)
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 姓名
     */
    private String name;

    /**
     * 联系电话
     */
    private String phone;

    /**
     * 电子邮件
     */
    private String email;

    /**
     * 授权
     */
    @Enumerated(EnumType.STRING)
    private AuthorityRole authority = AuthorityRole.ROLE_USER;

    /**
     * 是否启用
     */
    private boolean disabled = false;

    @Override
    @NonNull
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singleton(authority);
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    @NonNull
    public String getUsername() {
        return username;
    }
}
