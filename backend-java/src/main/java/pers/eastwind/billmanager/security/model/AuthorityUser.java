package pers.eastwind.billmanager.security.model;

import org.springframework.security.core.userdetails.UserDetails;

/**
 * 鉴权用户基类
 * <p>
 * 用户本身不持有角色，角色与权限由 SecurityService 在认证时组装。
 */
public interface AuthorityUser extends UserDetails {
    Integer getId();
}
