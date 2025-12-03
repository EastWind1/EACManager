package pers.eastwind.billmanager.user.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import pers.eastwind.billmanager.common.model.AuditEntity;

import java.util.Collection;
import java.util.Collections;

/**
 * 用户实体
 */
@Entity
@Table(name = "\"user\"") // 由于user是postgresql关键字，需用""括起来
@Data
@EqualsAndHashCode(callSuper = true)
public class User extends AuditEntity implements UserDetails {

    @Id
    @GeneratedValue
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
    private boolean isEnabled = true;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singleton(authority);
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }

}
