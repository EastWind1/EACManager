package pers.eastwind.billmanager.user.util;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import pers.eastwind.billmanager.user.model.AuthorityRole;
import pers.eastwind.billmanager.user.model.User;

/**
 * 鉴权工具
 */
public class AuthUtil {
    /**
     * 获取当前用户
     */
    public static User getCurUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetails) {
            return (User) auth.getPrincipal();
        }
        return null;
    }

    /**
     * 判断当前用户是否有指定角色
     * @param roles 角色列表
     * @return 是否有指定角色
     */
    public static boolean hasAnyRole(AuthorityRole... roles) {
        var curUser = getCurUser();
        return hasAnyRole(curUser, roles);
    }
    /**
     * 判断用户是否有指定角色
     * @param roles 角色列表
     * @return 是否有指定角色
     */
    public static boolean hasAnyRole(User user, AuthorityRole... roles) {
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