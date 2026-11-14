package pers.eastwind.billmanager.security.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AccountDTO {
    private Integer id;

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{3,31}$",
            message = "用户名必须为 4-32 位，以字母开头，仅包含字母、数字和下划线")
    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Pattern(regexp = "^$|^[a-zA-Z0-9*\\-_^~]{10,72}$",
            message = "密码长度必须为 10-72 位，且只能包含大写、小写字母、数字以及 *-_^~")
    private String password;

    private boolean disabled;
}
