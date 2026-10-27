package pers.eastwind.billmanager.user.model;

import org.mapstruct.*;
import pers.eastwind.billmanager.common.model.BaseMapper;
import pers.eastwind.billmanager.security.model.PermissionDTO;
import pers.eastwind.billmanager.security.model.RoleDTO;

import java.util.Collection;

/**
 * 密码、角色和权限属于安全模块数据，不通过普通 DTO 映射。
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper extends BaseMapper<User, UserDTO> {
    @Mapping(target = "password", ignore = true)
    UserDTO toDTO(User user, Collection<RoleDTO> roles, Collection<PermissionDTO> permissions);

    @Override
    @Mapping(target = "password", ignore = true)
    UserDTO toBaseDTO(User user);

    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    User toEntity(UserDTO userDTO);

    @Override
    @Mapping(target = "disabled", ignore = true)
    @Mapping(target = "password", ignore = true)
    void updateEntityFromDTO(UserDTO userDTO, @MappingTarget User user);
}
