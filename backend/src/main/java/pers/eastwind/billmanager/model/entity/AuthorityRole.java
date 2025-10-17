package pers.eastwind.billmanager.model.entity;

import org.springframework.security.core.GrantedAuthority;

/**
 * 权限实体
 */
public enum AuthorityRole implements GrantedAuthority {
    /**
     * 管理员
     */
    ROLE_ADMIN,
    /**
     * 普通用户
     */
    ROLE_USER,
    /**
     * 游客
     */
    ROLE_GUEST,
    /**
     * 财务
     */
    ROLE_FINANCE;

    @Override
    public String getAuthority() {
        return name(); // 返回 "ROLE_ADMIN" 这样的字符串
    }
}