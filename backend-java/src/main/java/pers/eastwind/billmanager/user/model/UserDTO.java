package pers.eastwind.billmanager.user.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import pers.eastwind.billmanager.security.model.AuthorityUser;

/**
 * 用户接口响应：继承统一鉴权账户，补充用户资料字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserDTO extends AuthorityUser {
    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名不能超过 64 个字符")
    private String name;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱不能超过 128 个字符")
    private String email;
}
