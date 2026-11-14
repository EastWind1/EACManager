package pers.eastwind.billmanager.user.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import pers.eastwind.billmanager.security.model.AuthorityUser;

/**
 * 用户接口响应：继承统一鉴权账户，补充用户资料字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserDTO extends AuthorityUser {
    private String name;
    private String phone;
    private String email;
}
