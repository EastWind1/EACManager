package com.eastwind.EACAfterSaleMgr.dto.mapper;

import com.eastwind.EACAfterSaleMgr.dto.UserDTO;
import com.eastwind.EACAfterSaleMgr.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface UserMapper {
    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    @Mapping(target = "password", ignore = true)
    UserDTO toUserDTO(User user);

    User toUser(UserDTO userDTO);
}
