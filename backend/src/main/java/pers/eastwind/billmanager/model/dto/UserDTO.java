package pers.eastwind.billmanager.model.dto;

import lombok.Data;
import pers.eastwind.billmanager.model.entity.AuthorityRole;


/**
 * 用户 DTO
 */
@Data
public class UserDTO {
    /**
     * 用户ID
     */
    private Integer id;
    /**
     * 用户名
     */
    private String username;
    /**
     * 密码
     * 除非必要，不允许向外传递
     */
    private String password;
    /**
     * 姓名
     */
    private String name;
    /**
     * 电话
     */
    private String phone;
    /**
     * 电子邮件
     */
    private String email;
    /**
     * 授权
     */
    private AuthorityRole authority;
}