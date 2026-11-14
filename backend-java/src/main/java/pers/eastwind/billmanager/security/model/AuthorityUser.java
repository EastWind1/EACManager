package pers.eastwind.billmanager.security.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 统一鉴权用户：认证主体
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class AuthorityUser extends AccountDTO implements UserDetails {
    private Collection<RoleDTO> roles;
    private Collection<PermissionDTO> permissions;

    @Override
    public boolean isEnabled() {
        return !isDisabled();
    }

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
