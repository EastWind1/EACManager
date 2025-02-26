package com.eastwind.ElevatorACAfterSaleManager.dto.mapper;

import com.eastwind.ElevatorACAfterSaleManager.dto.UserDTO;
import com.eastwind.ElevatorACAfterSaleManager.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface UserMapper {
    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    @Mapping(target = "password", ignore = true)
    UserDTO toUserDTO(User user);

    @Mapping(target = "lastModifiedDate", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    User toUser(UserDTO userDTO);
}
