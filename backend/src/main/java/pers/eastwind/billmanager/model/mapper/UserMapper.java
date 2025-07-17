package pers.eastwind.billmanager.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import pers.eastwind.billmanager.model.dto.UserDTO;
import pers.eastwind.billmanager.model.entity.User;

import java.util.List;

/**
 * 用户 Mapper
 * 由于安全性，密码不进行映射
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {
    @Mapping(target = "password", ignore = true)
    UserDTO toUserDTO(User user);

    @Mapping(target = "password", ignore = true)
    User toUser(UserDTO userDTO);

    @Mapping(target = "password", ignore = true)
    List<UserDTO> toUserDTOs(List<User> users);

    @Mapping(target = "password", ignore = true)
    void updateFromUserDTO(UserDTO userDTO, @MappingTarget User user);
}
