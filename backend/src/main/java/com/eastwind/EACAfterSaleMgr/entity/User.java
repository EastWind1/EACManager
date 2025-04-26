package com.eastwind.EACAfterSaleMgr.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

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
    @NotBlank(message = "用户名不能为空或空格")
    @Column(unique = true)
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空或空格")
    private String password;

    /**
     * 姓名
     */
    @NotBlank(message = "密码不能为空或空格")
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
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @Fetch(FetchMode.SUBSELECT)
    private Collection<Authority> authorities;

    /**
     * 是否启用
     */
    private boolean isEnabled = true;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
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
