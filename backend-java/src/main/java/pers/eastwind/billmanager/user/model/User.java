package pers.eastwind.billmanager.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jspecify.annotations.NonNull;
import pers.eastwind.billmanager.common.model.BaseUser;

/**
 * 用户实体
 */
@Entity
@Table(name = "sys_user",
        indexes = @Index(columnList = "username"))
@Data
@EqualsAndHashCode(callSuper = true)
public class User extends BaseUser {
    /**
     * 用户名
     */
    @Column(unique = true)
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 姓名
     */
    private String name;

    /**
     * 联系电话
     */
    private String phone;

    /**
     * 电子邮件
     */
    private String email;

    /**
     * 是否禁用
     */
    private boolean disabled;

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    @NonNull
    public String getUsername() {
        return username;
    }
}
