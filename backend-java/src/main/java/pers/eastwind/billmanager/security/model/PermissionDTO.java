package pers.eastwind.billmanager.security.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;

/**
 * 权限 DTO
 */
@Data
@AllArgsConstructor
public class PermissionDTO implements GrantedAuthority {
    private Integer id;
    private String code;
    private String module;
    private String moduleName;
    private String name;
    private Integer sortOrder;
    @Override
    @JsonIgnore
    public @Nullable String getAuthority() {
        return code;
    }
}
