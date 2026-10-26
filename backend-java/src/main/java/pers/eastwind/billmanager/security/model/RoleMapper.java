package pers.eastwind.billmanager.security.model;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import pers.eastwind.billmanager.common.model.BaseMapper;

/**
 * 角色 Mapper
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RoleMapper extends BaseMapper<Role, RoleDTO> {
}
