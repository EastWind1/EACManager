package pers.eastwind.billmanager.security.util;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import pers.eastwind.billmanager.security.model.AuthorityUser;

import java.util.HashSet;
import java.util.Set;

/**
 * 鉴权工具
 */
public class AuthUtil {
    /**
     * 获取当前用户
     */
    public static AuthorityUser getCurUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetails) {
            return (AuthorityUser) auth.getPrincipal();
        }
        return null;
    }

    /**
     * 是否拥有角色
     */
    public static boolean hasRole(String role) {
        if (!role.startsWith("ROLE_")) {
            role = "ROLE_" + role;
        }
        return hasAuthority(role);
    }

    /**
     * 是否拥有权限
     */
    public static boolean hasAuthority(String permission) {
        var curUser = getCurUser();
        if (curUser == null) {
            return false;
        }
        Set<String> permissionCode = new HashSet<>();
        for (GrantedAuthority authority : curUser.getAuthorities()) {
            permissionCode.add(authority.getAuthority());
        }
        return permissionCode.contains(permission);
    }
}
