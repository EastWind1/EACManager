package com.eastwind.ElevatorACAfterSaleManager.entity;

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
    private int id;

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

