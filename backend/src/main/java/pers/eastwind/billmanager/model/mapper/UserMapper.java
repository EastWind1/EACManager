package pers.eastwind.billmanager.model.mapper;

import org.mapstruct.*;
import pers.eastwind.billmanager.model.dto.UserDTO;
import pers.eastwind.billmanager.model.entity.User;

/**
 * 用户 Mapper
 * 由于安全性，密码不进行映射
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper extends BaseMapper<User, UserDTO> {
    @Override
    @Mapping(target = "password", ignore = true)
    UserDTO toDTO(User user);

    @Override
    @Mapping(target = "password", ignore = true)
    User toEntity(UserDTO userDTO);

    @Override
    @Mapping(target = "password", ignore = true)
    void updateEntityFromDTO(UserDTO userDTO, @MappingTarget User user);
}
