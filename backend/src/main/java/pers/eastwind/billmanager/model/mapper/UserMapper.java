package pers.eastwind.billmanager.model.mapper;

import pers.eastwind.billmanager.model.dto.UserDTO;
import pers.eastwind.billmanager.model.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * 用户 Mapper
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {
    @Mapping(target = "password", ignore = true)
    UserDTO toUserDTO(User user);

    User toUser(UserDTO userDTO);
}
