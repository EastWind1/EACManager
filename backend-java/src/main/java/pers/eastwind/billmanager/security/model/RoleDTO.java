package pers.eastwind.billmanager.security.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;

/**
 * 角色 DTO
 */
@Data
@AllArgsConstructor
public class RoleDTO implements GrantedAuthority {
    private Integer id;
    private String code;
    private String name;
    private Integer sortOrder;
    private boolean disabled;

    @Override
    @JsonIgnore
    public @Nullable String getAuthority() {
        return "ROLE_" + code;
    }
}
