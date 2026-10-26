package pers.eastwind.billmanager.user.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import pers.eastwind.billmanager.security.model.AuthorityUser;
import pers.eastwind.billmanager.security.model.PermissionDTO;
import pers.eastwind.billmanager.security.model.RoleDTO;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 用户 DTO
 */
@Data
public class UserDTO implements AuthorityUser {
    private Integer id;
    private String username;
    /**
     * 密码，除非必要不向外传递
     */
    private String password;
    private String name;
    private String phone;
    private String email;
    private boolean disabled;
    private Collection<RoleDTO> roles;
    private Collection<PermissionDTO> permissions;

    @JsonIgnore
    @Override
    public @NonNull Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> result = new ArrayList<>();
        if (roles != null) {
            result.addAll(roles);
        }
        if (permissions != null) {
            result.addAll(permissions);
        }
        return result;
    }
}
