package com.eastwind.EACAfterSaleMgr.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;

/**
 * 权限实体
 */
@Entity
@Data
public class Authority implements GrantedAuthority {
    @Id
    @GeneratedValue
    private Integer id;

    /**
     * 授权
     */
    @Enumerated(EnumType.STRING)
    private AuthorityRole authority;

    @Override
    public String getAuthority() {
        return authority.toString();
    }
}

