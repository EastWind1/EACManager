package pers.eastwind.billmanager.servicebill.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pers.eastwind.billmanager.user.model.UserDTO;

/**
 * 服务单负责人 / 指派候选人的最小信息，不携带用户的角色与权限。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssigneeDTO {
    private Integer id;
    private String username;
    private String name;

    public static AssigneeDTO from(UserDTO user) {
        if (user == null) {
            return null;
        }
        return new AssigneeDTO(user.getId(), user.getUsername(), user.getName());
    }
}
