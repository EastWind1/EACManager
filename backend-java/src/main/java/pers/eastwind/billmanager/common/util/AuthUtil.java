package pers.eastwind.billmanager.common.util;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import pers.eastwind.billmanager.common.model.AuthorityRole;
import pers.eastwind.billmanager.common.model.BaseUser;

/**
 * 鉴权工具
 */
public class AuthUtil {
    /**
     * 获取当前用户
     */
    public static BaseUser getCurUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetails) {
            return (BaseUser) auth.getPrincipal();
        }
        return null;
    }

    /**
     * 判断当前用户是否有指定角色
     *
     * @param roles 角色列表
     * @return 是否有指定角色
     */
    public static boolean hasAnyRole(AuthorityRole... roles) {
        var curUser = getCurUser();
        return hasAnyRole(curUser, roles);
    }

    /**
     * 判断用户是否有指定角色
     *
     * @param roles 角色列表
     * @return 是否有指定角色
     */
    public static boolean hasAnyRole(UserDetails user, AuthorityRole... roles) {
        if (user == null) {
            return false;
        }
        for (var role : roles) {
            if (user.getAuthorities().contains(role)) {
                return true;
            }
        }
        return false;
    }
}