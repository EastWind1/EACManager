package pers.eastwind.billmanager.security.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;

@Data
@AllArgsConstructor
public class RoleDTO implements GrantedAuthority {
    private Integer id;
    private String code;
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称不能超过 64 个字符")
    private String name;
    private Integer sortOrder;
    private boolean disabled;

    @Override
    @JsonIgnore
    public @Nullable String getAuthority() {
        return "ROLE_" + code;
    }
}
